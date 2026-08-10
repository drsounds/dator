package se.spacify.dator.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import se.spacify.dator.model.DatorColumn;
import se.spacify.dator.model.DatorTable;

/**
 * Generic row-level CRUD against a physical SQLite table, driven entirely by
 * a DatorTable model and its DatorColumn list. Every row is addressed by
 * SQLite's implicit "rowid" so that models work regardless of whether they
 * declare a primary key.
 */
public class DataRepository {

    public static final String ROWID = "__rowid";

    private final Database db;

    public DataRepository(Database db) {
        this.db = db;
    }

    public List<Map<String, Object>> listRows(DatorTable table, List<DatorColumn> columns) throws SQLException {
        String sql = "SELECT rowid AS " + MetaRepository.quote(ROWID) + selectColumns(columns) +
                " FROM " + MetaRepository.quote(table.getName()) + " ORDER BY rowid";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
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

    public long insertRow(DatorTable table, Map<String, Object> values) throws SQLException {
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

    public void updateRow(DatorTable table, long rowid, Map<String, Object> values) throws SQLException {
        if (values.isEmpty()) {
            return;
        }
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

    public void deleteRow(DatorTable table, long rowid) throws SQLException {
        String sql = "DELETE FROM " + MetaRepository.quote(table.getName()) + " WHERE rowid=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, rowid);
            ps.executeUpdate();
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

    private String selectColumns(List<DatorColumn> columns) {
        StringBuilder sb = new StringBuilder();
        for (DatorColumn c : columns) {
            sb.append(", ").append(MetaRepository.quote(c.getName()));
        }
        return sb.toString();
    }

    /** Connection accessor for callers (e.g. relation combo lookups) that need direct queries. */
    Connection connection() {
        return db.getConnection();
    }
}
