package se.spacify.dator.db;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import se.spacify.dator.model.DatorColumn;
import se.spacify.dator.model.DatorSummary;
import se.spacify.dator.model.DatorTable;

/**
 * Generic row-level CRUD against a physical SQLite table, driven entirely by
 * a DatorTable model and its DatorColumn list. Every row is addressed by
 * SQLite's implicit "rowid" so that models work regardless of whether they
 * declare a primary key.
 *
 * <p>Tables that carry the standard bookkeeping columns (see
 * {@link MetaRepository#STANDARD_COLUMN_NAMES}) get extra behavior for
 * free: {@code created}/{@code updated} are stamped automatically,
 * {@code number} is auto-assigned for ordering, {@code deleted} turns
 * {@link #deleteRow} into a soft delete, and every write is journaled to
 * dator_events as a hash-chained, tamper-evident log.</p>
 */
public class DataRepository {

    public static final String ROWID = "__rowid";

    private static final String GENESIS_HASH = "0".repeat(64);
    private static final Set<String> ALLOWED_AGGREGATES = Set.of("SUM", "AVG", "COUNT", "MIN", "MAX");
    private static final String BASE62_ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int SLUG_LENGTH = 10;

    private final Database db;
    private final SecureRandom random = new SecureRandom();

    public DataRepository(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    public List<Map<String, Object>> listRows(DatorTable table, List<DatorColumn> columns) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT rowid AS ").append(MetaRepository.quote(ROWID))
                .append(selectColumns(columns))
                .append(" FROM ").append(MetaRepository.quote(table.getName()));
        String deletedFilter = deletedFilterSql(columns);
        if (deletedFilter != null) {
            sql.append(" WHERE ").append(deletedFilter);
        }
        sql.append(" ORDER BY ");
        DatorColumn sortColumn = table.getSortColumnId() == null ? null : columnById(columns, table.getSortColumnId());
        if (sortColumn != null) {
            sql.append(MetaRepository.quote(sortColumn.getName()))
                    .append(DatorTable.SORT_DESC.equalsIgnoreCase(table.getSortDirection()) ? " DESC" : " ASC")
                    .append(", ");
        } else if (hasColumn(columns, "number")) {
            sql.append(MetaRepository.quote("number")).append(" IS NULL, ")
                    .append(MetaRepository.quote("number")).append(", ");
        }
        sql.append("rowid");

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql.toString())) {
            ResultSetMetaData md = rs.getMetaData();
            int colCount = md.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(md.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    public Map<String, Object> getRow(DatorTable table, List<DatorColumn> columns, long rowid) throws SQLException {
        String sql = "SELECT rowid AS " + MetaRepository.quote(ROWID) + selectColumns(columns) +
                " FROM " + MetaRepository.quote(table.getName()) + " WHERE rowid=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, rowid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                ResultSetMetaData md = rs.getMetaData();
                int colCount = md.getColumnCount();
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(md.getColumnLabel(i), rs.getObject(i));
                }
                return row;
            }
        }
    }

    public int countRows(DatorTable table) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + MetaRepository.quote(table.getName());
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Runs one aggregate per summary (SUM/AVG/COUNT/MIN/MAX over its target column) in a single query. */
    public Map<Integer, Object> computeSummaries(DatorTable table, List<DatorColumn> columns,
            List<DatorSummary> summaries) throws SQLException {
        Map<Integer, Object> result = new LinkedHashMap<>();
        List<DatorSummary> valid = new ArrayList<>();
        List<DatorColumn> targets = new ArrayList<>();
        for (DatorSummary s : summaries) {
            if (!ALLOWED_AGGREGATES.contains(s.getAggregate())) {
                continue;
            }
            DatorColumn col = columnById(columns, s.getColumnId());
            if (col != null) {
                valid.add(s);
                targets.add(col);
            }
        }
        if (valid.isEmpty()) {
            return result;
        }

        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < valid.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(valid.get(i).getAggregate()).append("(")
                    .append(MetaRepository.quote(targets.get(i).getName())).append(")");
        }
        sql.append(" FROM ").append(MetaRepository.quote(table.getName()));
        String deletedFilter = deletedFilterSql(columns);
        if (deletedFilter != null) {
            sql.append(" WHERE ").append(deletedFilter);
        }

        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql.toString())) {
            if (rs.next()) {
                for (int i = 0; i < valid.size(); i++) {
                    result.put(valid.get(i).getId(), rs.getObject(i + 1));
                }
            }
        }
        return result;
    }

    /**
     * "Not deleted" predicate, tolerant of both the current convention
     * (nullable TEXT timestamp, NULL = not deleted) and the legacy one
     * (NOT NULL INTEGER flag, 0 = not deleted) for tables created before
     * the column's type changed.
     */
    private String deletedFilterSql(List<DatorColumn> columns) {
        DatorColumn deleted = columnByName(columns, "deleted");
        if (deleted == null) {
            return null;
        }
        String quoted = MetaRepository.quote("deleted");
        return "INTEGER".equalsIgnoreCase(deleted.getDataType()) ? quoted + "=0" : quoted + " IS NULL";
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    public long insertRow(DatorTable table, List<DatorColumn> columns, Map<String, Object> values) throws SQLException {
        Map<String, Object> toWrite = new LinkedHashMap<>(values);
        if (hasColumn(columns, "created") && !toWrite.containsKey("created")) {
            toWrite.put("created", nowTimestamp());
        }
        if (hasColumn(columns, "number") && !toWrite.containsKey("number")) {
            toWrite.put("number", nextNumber(table));
        }
        if (hasColumn(columns, "uuid") && !toWrite.containsKey("uuid")) {
            toWrite.put("uuid", UUID.randomUUID().toString());
        }
        if (hasColumn(columns, "slug") && !toWrite.containsKey("slug")) {
            toWrite.put("slug", randomBase62(SLUG_LENGTH));
        }

        Connection conn = db.getConnection();
        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            long rowid = doInsert(table, toWrite);
            for (Map.Entry<String, Object> e : toWrite.entrySet()) {
                logEvent(table.getName(), "insert", rowid, columnByName(columns, e.getKey()), null, e.getValue());
            }
            conn.commit();
            return rowid;
        } catch (RuntimeException | SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }

    public void updateRow(DatorTable table, List<DatorColumn> columns, long rowid, Map<String, Object> values)
            throws SQLException {
        Map<String, Object> toWrite = new LinkedHashMap<>(values);
        if (hasColumn(columns, "updated")) {
            toWrite.put("updated", nowTimestamp());
        }
        if (toWrite.isEmpty()) {
            return;
        }

        Map<String, Object> before = getRow(table, columns, rowid);

        Connection conn = db.getConnection();
        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            doUpdate(table, rowid, toWrite);
            for (Map.Entry<String, Object> e : toWrite.entrySet()) {
                Object oldValue = before == null ? null : before.get(e.getKey());
                Object newValue = e.getValue();
                if (!valuesEqual(oldValue, newValue)) {
                    logEvent(table.getName(), "update", rowid, columnByName(columns, e.getKey()), oldValue, newValue);
                }
            }
            conn.commit();
        } catch (RuntimeException | SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }

    /**
     * Soft-deletes when the table has a "deleted" column: stamps the
     * current timestamp (or, for tables predating this convention, sets
     * the legacy INTEGER flag to 1). Otherwise a real DELETE.
     */
    public void deleteRow(DatorTable table, List<DatorColumn> columns, long rowid) throws SQLException {
        DatorColumn deletedColumn = columnByName(columns, "deleted");
        if (deletedColumn != null) {
            Map<String, Object> values = new LinkedHashMap<>();
            Object deletedValue = "INTEGER".equalsIgnoreCase(deletedColumn.getDataType())
                    ? (Object) 1L : (Object) nowTimestamp();
            values.put("deleted", deletedValue);
            updateRow(table, columns, rowid, values);
            return;
        }

        Connection conn = db.getConnection();
        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM " + MetaRepository.quote(table.getName()) + " WHERE rowid=?")) {
                ps.setLong(1, rowid);
                ps.executeUpdate();
            }
            logEvent(table.getName(), "delete", rowid, null, null, null);
            conn.commit();
        } catch (RuntimeException | SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }

    private long doInsert(DatorTable table, Map<String, Object> values) throws SQLException {
        if (values.isEmpty()) {
            try (Statement st = db.getConnection().createStatement()) {
                st.executeUpdate("INSERT INTO " + MetaRepository.quote(table.getName()) + " DEFAULT VALUES");
            }
        } else {
            List<String> names = new ArrayList<>(values.keySet());
            StringBuilder sql = new StringBuilder("INSERT INTO ").append(MetaRepository.quote(table.getName()))
                    .append(" (");
            List<String> placeholders = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(MetaRepository.quote(names.get(i)));
                placeholders.add("?");
            }
            sql.append(") VALUES (").append(String.join(", ", placeholders)).append(")");
            try (PreparedStatement ps = db.getConnection().prepareStatement(sql.toString())) {
                int i = 1;
                for (String name : names) {
                    ps.setObject(i++, values.get(name));
                }
                ps.executeUpdate();
            }
        }
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void doUpdate(DatorTable table, long rowid, Map<String, Object> values) throws SQLException {
        List<String> names = new ArrayList<>(values.keySet());
        StringBuilder sql = new StringBuilder("UPDATE ").append(MetaRepository.quote(table.getName())).append(" SET ");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(MetaRepository.quote(names.get(i))).append("=?");
        }
        sql.append(" WHERE rowid=?");
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql.toString())) {
            int i = 1;
            for (String name : names) {
                ps.setObject(i++, values.get(name));
            }
            ps.setLong(i, rowid);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean hasColumn(List<DatorColumn> columns, String name) {
        return columnByName(columns, name) != null;
    }

    private DatorColumn columnByName(List<DatorColumn> columns, String name) {
        for (DatorColumn c : columns) {
            if (c.getName().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }

    private DatorColumn columnById(List<DatorColumn> columns, int id) {
        for (DatorColumn c : columns) {
            if (c.getId() == id) {
                return c;
            }
        }
        return null;
    }

    private boolean valuesEqual(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return String.valueOf(a).equals(String.valueOf(b));
    }

    private String nowTimestamp() throws SQLException {
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT datetime('now')")) {
            rs.next();
            return rs.getString(1);
        }
    }

    private String randomBase62(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(BASE62_ALPHABET.charAt(random.nextInt(BASE62_ALPHABET.length())));
        }
        return sb.toString();
    }

    private long nextNumber(DatorTable table) throws SQLException {
        String sql = "SELECT COALESCE(MAX(" + MetaRepository.quote("number") + "), 0) + 1 FROM " +
                MetaRepository.quote(table.getName());
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String selectColumns(List<DatorColumn> columns) {
        StringBuilder sb = new StringBuilder();
        for (DatorColumn c : columns) {
            sb.append(", ").append(MetaRepository.quote(c.getName()));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Event journal (dator_events): append-only, hash-chained audit log
    // ------------------------------------------------------------------

    private void logEvent(String subject, String predicate, long objectId, DatorColumn column,
            Object oldValue, Object newValue) throws SQLException {
        String prevHash = lastHash();
        String created = nowTimestamp();
        Integer columnId = column == null ? null : column.getId();
        String columnName = column == null ? null : column.getName();
        String oldStr = oldValue == null ? null : String.valueOf(oldValue);
        String newStr = newValue == null ? null : String.valueOf(newValue);
        String hash = computeHash(prevHash, created, subject, predicate, objectId, columnId, columnName, oldStr, newStr);

        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT INTO dator_events(created, subject, predicate, object_id, column_id, column_name, " +
                        "old_value, new_value, prev_hash, hash) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, created);
            ps.setString(2, subject);
            ps.setString(3, predicate);
            ps.setLong(4, objectId);
            if (columnId == null) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setInt(5, columnId);
            }
            ps.setString(6, columnName);
            ps.setString(7, oldStr);
            ps.setString(8, newStr);
            ps.setString(9, prevHash);
            ps.setString(10, hash);
            ps.executeUpdate();
        }
    }

    private String lastHash() throws SQLException {
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT hash FROM dator_events ORDER BY id DESC LIMIT 1")) {
            if (rs.next()) {
                return rs.getString(1);
            }
        }
        return GENESIS_HASH;
    }

    private String computeHash(String prevHash, String created, String subject, String predicate, long objectId,
            Integer columnId, String columnName, String oldValue, String newValue) {
        String canonical = String.join("|",
                prevHash, created, subject, predicate, String.valueOf(objectId),
                String.valueOf(columnId), String.valueOf(columnName),
                String.valueOf(oldValue), String.valueOf(newValue));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
