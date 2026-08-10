package se.spacify.dator.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import se.spacify.dator.model.DatorColumn;
import se.spacify.dator.model.DatorRelation;
import se.spacify.dator.model.DatorSummary;
import se.spacify.dator.model.DatorTable;

/**
 * Reads and writes the dator_tables / dator_table_columns /
 * dator_table_relations meta tables, and keeps the physical SQLite schema
 * (the actual data tables) in sync with those models.
 */
public class MetaRepository {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    public static final List<String> COLUMN_TYPES =
            List.of("TEXT", "LONGTEXT", "INTEGER", "REAL", "BLOB", "NUMERIC");

    private static final Set<String> ALLOWED_TYPES = new HashSet<>(COLUMN_TYPES);

    private static final Set<String> RESERVED_NAMES = new HashSet<>(List.of(
            "dator_tables", "dator_table_columns", "dator_table_relations",
            "dator_table_summaries", "dator_events",
            "sqlite_master", "sqlite_sequence"));

    /** Columns automatically ensured on every data table. */
    public static final List<String> STANDARD_COLUMN_NAMES =
            List.of("parent_id", "number", "created", "updated", "deleted", "uuid", "slug");

    /** Standard columns hidden by default from list/grid views (still fully usable otherwise). */
    public static final Set<String> HIDDEN_LIST_COLUMNS = new HashSet<>(STANDARD_COLUMN_NAMES);

    private final Database db;

    public MetaRepository(Database db) throws SQLException {
        this.db = db;
        ensureSchema();
    }

    private void ensureSchema() throws SQLException {
        db.execute("CREATE TABLE IF NOT EXISTS dator_tables (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL UNIQUE, " +
                "label TEXT, " +
                "sort_column_id INTEGER REFERENCES dator_table_columns(id) ON DELETE SET NULL, " +
                "sort_direction TEXT NOT NULL DEFAULT 'asc', " +
                "list_view_mode TEXT NOT NULL DEFAULT 'table', " +
                "created_at TEXT NOT NULL DEFAULT (datetime('now')))");

        db.execute("CREATE TABLE IF NOT EXISTS dator_table_columns (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "table_id INTEGER NOT NULL REFERENCES dator_tables(id) ON DELETE CASCADE, " +
                "name TEXT NOT NULL, " +
                "label TEXT, " +
                "data_type TEXT NOT NULL, " +
                "is_pk INTEGER NOT NULL DEFAULT 0, " +
                "is_nullable INTEGER NOT NULL DEFAULT 1, " +
                "is_unique INTEGER NOT NULL DEFAULT 0, " +
                "default_value TEXT, " +
                "ordinal INTEGER NOT NULL DEFAULT 0, " +
                "UNIQUE(table_id, name))");

        db.execute("CREATE TABLE IF NOT EXISTS dator_table_relations (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "table_id INTEGER NOT NULL REFERENCES dator_tables(id) ON DELETE CASCADE, " +
                "column_id INTEGER NOT NULL REFERENCES dator_table_columns(id) ON DELETE CASCADE, " +
                "ref_table_id INTEGER NOT NULL REFERENCES dator_tables(id) ON DELETE CASCADE, " +
                "ref_column_id INTEGER REFERENCES dator_table_columns(id) ON DELETE SET NULL, " +
                "relation_type TEXT NOT NULL DEFAULT 'many-to-one', " +
                "display_mode TEXT NOT NULL DEFAULT 'tab', " +
                "label TEXT)");

        db.execute("CREATE TABLE IF NOT EXISTS dator_table_summaries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "table_id INTEGER NOT NULL REFERENCES dator_tables(id) ON DELETE CASCADE, " +
                "column_id INTEGER NOT NULL REFERENCES dator_table_columns(id) ON DELETE CASCADE, " +
                "aggregate TEXT NOT NULL, " +
                "position TEXT NOT NULL DEFAULT 'bottom', " +
                "label TEXT)");

        // Append-only event journal: no foreign keys, so history survives
        // schema edits and is never cascade-deleted.
        db.execute("CREATE TABLE IF NOT EXISTS dator_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "created TEXT NOT NULL DEFAULT (datetime('now')), " +
                "subject TEXT NOT NULL, " +
                "predicate TEXT NOT NULL, " +
                "object_id INTEGER NOT NULL, " +
                "column_id INTEGER, " +
                "column_name TEXT, " +
                "old_value TEXT, " +
                "new_value TEXT, " +
                "prev_hash TEXT NOT NULL, " +
                "hash TEXT NOT NULL)");

        migrateRelationsDisplayMode();
        migrateTableSortColumns();
    }

    /** Adds display_mode to dator_table_relations for databases created before it existed. */
    private void migrateRelationsDisplayMode() throws SQLException {
        addColumnIfMissing("dator_table_relations", "display_mode",
                "ALTER TABLE dator_table_relations ADD COLUMN display_mode TEXT NOT NULL DEFAULT 'tab'");
    }

    /** Adds sort_column_id/sort_direction/list_view_mode to dator_tables for older databases. */
    private void migrateTableSortColumns() throws SQLException {
        addColumnIfMissing("dator_tables", "sort_column_id",
                "ALTER TABLE dator_tables ADD COLUMN sort_column_id INTEGER " +
                        "REFERENCES dator_table_columns(id) ON DELETE SET NULL");
        addColumnIfMissing("dator_tables", "sort_direction",
                "ALTER TABLE dator_tables ADD COLUMN sort_direction TEXT NOT NULL DEFAULT 'asc'");
        addColumnIfMissing("dator_tables", "list_view_mode",
                "ALTER TABLE dator_tables ADD COLUMN list_view_mode TEXT NOT NULL DEFAULT 'table'");
    }

    private void addColumnIfMissing(String table, String column, String alterSql) throws SQLException {
        boolean hasColumn = false;
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    hasColumn = true;
                    break;
                }
            }
        }
        if (!hasColumn) {
            db.execute(alterSql);
        }
    }

    // ------------------------------------------------------------------
    // Validation helpers
    // ------------------------------------------------------------------

    public static void validateIdentifier(String name) {
        if (name == null || !IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("\"" + name + "\" is not a valid identifier " +
                    "(letters, digits, underscore; must not start with a digit)");
        }
        if (RESERVED_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("\"" + name + "\" is reserved and cannot be used");
        }
    }

    public static void validateType(String type) {
        if (type == null || !ALLOWED_TYPES.contains(type.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("\"" + type + "\" is not a supported column type");
        }
    }

    static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    private static final String TABLE_COLUMNS =
            "id, name, label, sort_column_id, sort_direction, list_view_mode";

    private DatorTable readTable(ResultSet rs) throws SQLException {
        DatorTable t = new DatorTable(rs.getInt("id"), rs.getString("name"), rs.getString("label"));
        int sortColumnId = rs.getInt("sort_column_id");
        t.setSortColumnId(rs.wasNull() ? null : sortColumnId);
        t.setSortDirection(rs.getString("sort_direction"));
        t.setListViewMode(rs.getString("list_view_mode"));
        return t;
    }

    public List<DatorTable> listTables() throws SQLException {
        List<DatorTable> result = new ArrayList<>();
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT " + TABLE_COLUMNS + " FROM dator_tables ORDER BY name")) {
            while (rs.next()) {
                result.add(readTable(rs));
            }
        }
        return result;
    }

    public DatorTable getTable(int id) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT " + TABLE_COLUMNS + " FROM dator_tables WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readTable(rs);
                }
            }
        }
        return null;
    }

    public List<DatorColumn> listColumns(int tableId) throws SQLException {
        List<DatorColumn> result = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT id, table_id, name, label, data_type, is_pk, is_nullable, is_unique, " +
                        "default_value, ordinal FROM dator_table_columns " +
                        "WHERE table_id=? ORDER BY ordinal, id")) {
            ps.setInt(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DatorColumn c = new DatorColumn();
                    c.setId(rs.getInt("id"));
                    c.setTableId(rs.getInt("table_id"));
                    c.setName(rs.getString("name"));
                    c.setLabel(rs.getString("label"));
                    c.setDataType(rs.getString("data_type"));
                    c.setPk(rs.getInt("is_pk") != 0);
                    c.setNullable(rs.getInt("is_nullable") != 0);
                    c.setUnique(rs.getInt("is_unique") != 0);
                    c.setDefaultValue(rs.getString("default_value"));
                    c.setOrdinal(rs.getInt("ordinal"));
                    result.add(c);
                }
            }
        }
        return result;
    }

    public List<DatorRelation> listRelations(int tableId) throws SQLException {
        List<DatorRelation> result = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT id, table_id, column_id, ref_table_id, ref_column_id, relation_type, " +
                        "display_mode, label FROM dator_table_relations WHERE table_id=? ORDER BY id")) {
            ps.setInt(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(readRelation(rs));
                }
            }
        }
        return result;
    }

    /** Relations on other tables that point at this table (for information only). */
    public List<DatorRelation> listRelationsReferencing(int refTableId) throws SQLException {
        List<DatorRelation> result = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT id, table_id, column_id, ref_table_id, ref_column_id, relation_type, " +
                        "display_mode, label FROM dator_table_relations WHERE ref_table_id=? ORDER BY id")) {
            ps.setInt(1, refTableId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(readRelation(rs));
                }
            }
        }
        return result;
    }

    public List<DatorSummary> listSummaries(int tableId) throws SQLException {
        List<DatorSummary> result = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT id, table_id, column_id, aggregate, position, label " +
                        "FROM dator_table_summaries WHERE table_id=? ORDER BY id")) {
            ps.setInt(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DatorSummary s = new DatorSummary();
                    s.setId(rs.getInt("id"));
                    s.setTableId(rs.getInt("table_id"));
                    s.setColumnId(rs.getInt("column_id"));
                    s.setAggregate(rs.getString("aggregate"));
                    s.setPosition(rs.getString("position"));
                    s.setLabel(rs.getString("label"));
                    result.add(s);
                }
            }
        }
        return result;
    }

    private DatorRelation readRelation(ResultSet rs) throws SQLException {
        DatorRelation r = new DatorRelation();
        r.setId(rs.getInt("id"));
        r.setTableId(rs.getInt("table_id"));
        r.setColumnId(rs.getInt("column_id"));
        r.setRefTableId(rs.getInt("ref_table_id"));
        int refCol = rs.getInt("ref_column_id");
        r.setRefColumnId(rs.wasNull() ? null : refCol);
        r.setRelationType(rs.getString("relation_type"));
        r.setDisplayMode(rs.getString("display_mode"));
        r.setLabel(rs.getString("label"));
        return r;
    }

    // ------------------------------------------------------------------
    // Writing: table models
    // ------------------------------------------------------------------

    /**
     * Creates or updates a table model and keeps the physical SQLite table
     * in sync with the given column list. Columns with id&lt;=0 are treated
     * as new; existing columns not present in newColumns are dropped.
     */
    public DatorTable saveTableModel(DatorTable table, List<DatorColumn> newColumns) throws SQLException {
        ensureStandardColumns(newColumns);
        if (newColumns.isEmpty()) {
            throw new IllegalArgumentException("A table needs at least one column");
        }
        validateIdentifier(table.getName());
        for (DatorColumn c : newColumns) {
            validateIdentifier(c.getName());
            validateType(c.getDataType());
        }
        Set<String> seenNames = new HashSet<>();
        for (DatorColumn c : newColumns) {
            if (!seenNames.add(c.getName().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate column name \"" + c.getName() + "\"");
            }
        }

        Connection conn = db.getConnection();
        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            boolean isNewTable = table.getId() <= 0;
            String oldPhysicalName = null;
            List<DatorColumn> oldColumns = new ArrayList<>();

            if (!isNewTable) {
                DatorTable existing = getTable(table.getId());
                if (existing == null) {
                    throw new IllegalStateException("Table model no longer exists");
                }
                oldPhysicalName = existing.getName();
                oldColumns = listColumns(table.getId());
            } else {
                if (findTableByName(table.getName()) != null) {
                    throw new IllegalArgumentException("A table model named \"" + table.getName() + "\" already exists");
                }
            }

            if (isNewTable) {
                table.setId(insertTableRow(table));
            } else {
                updateTableRow(table);
            }

            syncColumnsMeta(table.getId(), oldColumns, newColumns);
            List<DatorColumn> finalColumns = listColumns(table.getId());
            rebuildPhysicalTable(oldPhysicalName, table.getName(), oldColumns, finalColumns);
            ensureSelfParentRelation(table.getId(), finalColumns);

            conn.commit();
            return table;
        } catch (RuntimeException | SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }

    /** Fresh copies of the standard bookkeeping columns every table gets. */
    public static List<DatorColumn> standardColumnDefs() {
        List<DatorColumn> list = new ArrayList<>();

        DatorColumn uuid = new DatorColumn("uuid", "TEXT");
        uuid.setLabel("UUID");
        uuid.setNullable(false);
        uuid.setUnique(true);
        list.add(uuid);

        DatorColumn slug = new DatorColumn("slug", "TEXT");
        slug.setLabel("Slug");
        slug.setNullable(false);
        slug.setUnique(true);
        list.add(slug);

        DatorColumn parentId = new DatorColumn("parent_id", "INTEGER");
        parentId.setLabel("Parent");
        parentId.setNullable(true);
        list.add(parentId);

        DatorColumn number = new DatorColumn("number", "INTEGER");
        number.setLabel("Number");
        number.setNullable(true);
        list.add(number);

        DatorColumn created = new DatorColumn("created", "TEXT");
        created.setLabel("Created");
        created.setNullable(false);
        list.add(created);

        DatorColumn updated = new DatorColumn("updated", "TEXT");
        updated.setLabel("Updated");
        updated.setNullable(true);
        list.add(updated);

        // Nullable timestamp: NULL means "not deleted", a datetime value
        // records when it was soft-deleted.
        DatorColumn deleted = new DatorColumn("deleted", "TEXT");
        deleted.setLabel("Deleted");
        deleted.setNullable(true);
        list.add(deleted);

        return list;
    }

    /** Appends any of the standard columns missing from this list (matched by name, case-insensitive). */
    public static void ensureStandardColumns(List<DatorColumn> columns) {
        Set<String> existing = new HashSet<>();
        for (DatorColumn c : columns) {
            existing.add(c.getName().toLowerCase(Locale.ROOT));
        }
        for (DatorColumn std : standardColumnDefs()) {
            if (!existing.contains(std.getName().toLowerCase(Locale.ROOT))) {
                columns.add(std);
            }
        }
    }

    /**
     * Every table gets a parent_id column (for tree-shaped parent/child
     * rows); wire it up as a self-referencing relation, shown inline as
     * "Children" in the single-row view, so the pairing is useful out of
     * the box. No-op if the relation already exists.
     */
    private void ensureSelfParentRelation(int tableId, List<DatorColumn> columns) throws SQLException {
        DatorColumn parentIdCol = null;
        DatorColumn pk = null;
        for (DatorColumn c : columns) {
            if (c.getName().equalsIgnoreCase("parent_id")) {
                parentIdCol = c;
            }
            if (c.isPk()) {
                pk = c;
            }
        }
        if (parentIdCol == null) {
            return;
        }

        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT 1 FROM dator_table_relations WHERE table_id=? AND column_id=? AND ref_table_id=?")) {
            ps.setInt(1, tableId);
            ps.setInt(2, parentIdCol.getId());
            ps.setInt(3, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }

        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT INTO dator_table_relations(table_id, column_id, ref_table_id, ref_column_id, " +
                        "relation_type, display_mode, label) VALUES (?,?,?,?,?,?,?)")) {
            ps.setInt(1, tableId);
            ps.setInt(2, parentIdCol.getId());
            ps.setInt(3, tableId);
            if (pk != null) {
                ps.setInt(4, pk.getId());
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
            }
            ps.setString(5, DatorRelation.MANY_TO_ONE);
            ps.setString(6, DatorRelation.DISPLAY_TABLE);
            ps.setString(7, "Children");
            ps.executeUpdate();
        }
    }

    public void deleteTableModel(DatorTable table) throws SQLException {
        Connection conn = db.getConnection();
        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            db.execute("DROP TABLE IF EXISTS " + quote(table.getName()));
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM dator_tables WHERE id=?")) {
                ps.setInt(1, table.getId());
                ps.executeUpdate();
            }
            conn.commit();
        } catch (RuntimeException | SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }

    public DatorTable findTableByName(String name) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT " + TABLE_COLUMNS + " FROM dator_tables WHERE name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readTable(rs);
                }
            }
        }
        return null;
    }

    /** Persists just the sort column/direction, without touching the physical schema. */
    public void updateTableSort(DatorTable table) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE dator_tables SET sort_column_id=?, sort_direction=? WHERE id=?")) {
            if (table.getSortColumnId() == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, table.getSortColumnId());
            }
            ps.setString(2, table.getSortDirection() == null ? DatorTable.SORT_ASC : table.getSortDirection());
            ps.setInt(3, table.getId());
            ps.executeUpdate();
        }
    }

    /** Persists just the list view mode (table/card), without touching the physical schema. */
    public void updateListViewMode(DatorTable table) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE dator_tables SET list_view_mode=? WHERE id=?")) {
            ps.setString(1, table.getListViewMode() == null ? DatorTable.VIEW_TABLE : table.getListViewMode());
            ps.setInt(2, table.getId());
            ps.executeUpdate();
        }
    }

    private int insertTableRow(DatorTable table) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT INTO dator_tables(name, label) VALUES (?, ?)")) {
            ps.setString(1, table.getName());
            ps.setString(2, table.getLabel());
            ps.executeUpdate();
        }
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void updateTableRow(DatorTable table) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE dator_tables SET name=?, label=? WHERE id=?")) {
            ps.setString(1, table.getName());
            ps.setString(2, table.getLabel());
            ps.setInt(3, table.getId());
            ps.executeUpdate();
        }
    }

    private void syncColumnsMeta(int tableId, List<DatorColumn> oldColumns, List<DatorColumn> newColumns)
            throws SQLException {
        Set<Integer> oldIds = new HashSet<>();
        for (DatorColumn c : oldColumns) {
            oldIds.add(c.getId());
        }
        Set<Integer> keptIds = new HashSet<>();

        int ordinal = 0;
        for (DatorColumn c : newColumns) {
            if (c.getId() > 0 && oldIds.contains(c.getId())) {
                keptIds.add(c.getId());
                try (PreparedStatement ps = db.getConnection().prepareStatement(
                        "UPDATE dator_table_columns SET name=?, label=?, data_type=?, is_pk=?, " +
                                "is_nullable=?, is_unique=?, default_value=?, ordinal=? WHERE id=?")) {
                    ps.setString(1, c.getName());
                    ps.setString(2, c.getLabel());
                    ps.setString(3, c.getDataType().toUpperCase(Locale.ROOT));
                    ps.setInt(4, c.isPk() ? 1 : 0);
                    ps.setInt(5, c.isNullable() ? 1 : 0);
                    ps.setInt(6, c.isUnique() ? 1 : 0);
                    ps.setString(7, c.getDefaultValue());
                    ps.setInt(8, ordinal);
                    ps.setInt(9, c.getId());
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = db.getConnection().prepareStatement(
                        "INSERT INTO dator_table_columns(table_id, name, label, data_type, is_pk, " +
                                "is_nullable, is_unique, default_value, ordinal) VALUES (?,?,?,?,?,?,?,?,?)")) {
                    ps.setInt(1, tableId);
                    ps.setString(2, c.getName());
                    ps.setString(3, c.getLabel());
                    ps.setString(4, c.getDataType().toUpperCase(Locale.ROOT));
                    ps.setInt(5, c.isPk() ? 1 : 0);
                    ps.setInt(6, c.isNullable() ? 1 : 0);
                    ps.setInt(7, c.isUnique() ? 1 : 0);
                    ps.setString(8, c.getDefaultValue());
                    ps.setInt(9, ordinal);
                    ps.executeUpdate();
                }
            }
            ordinal++;
        }

        for (Integer oldId : oldIds) {
            if (!keptIds.contains(oldId)) {
                try (PreparedStatement ps = db.getConnection().prepareStatement(
                        "DELETE FROM dator_table_columns WHERE id=?")) {
                    ps.setInt(1, oldId);
                    ps.executeUpdate();
                }
            }
        }
    }

    /**
     * Rebuilds the physical table so it matches newColumns. If the table did
     * not exist before, it is simply created. Otherwise a temp table with the
     * new schema is created, matching data is copied across (matched by
     * column id), the old table is dropped and the temp table renamed.
     */
    private void rebuildPhysicalTable(String oldPhysicalName, String finalName,
            List<DatorColumn> oldColumns, List<DatorColumn> newColumns) throws SQLException {
        boolean oldExists = oldPhysicalName != null && db.tableExists(oldPhysicalName);

        if (!oldExists) {
            db.execute(buildCreateTableSql(finalName, newColumns));
            return;
        }

        String tempName = "__dator_tmp_" + finalName;
        db.execute("DROP TABLE IF EXISTS " + quote(tempName));
        db.execute(buildCreateTableSql(tempName, newColumns));

        Map<Integer, String> oldNamesById = new HashMap<>();
        for (DatorColumn c : oldColumns) {
            oldNamesById.put(c.getId(), c.getName());
        }

        List<String> destCols = new ArrayList<>();
        List<String> srcCols = new ArrayList<>();
        for (DatorColumn c : newColumns) {
            String oldName = oldNamesById.get(c.getId());
            if (oldName != null) {
                destCols.add(quote(c.getName()));
                srcCols.add(quote(oldName));
            }
        }

        if (!destCols.isEmpty()) {
            String sql = "INSERT INTO " + quote(tempName) + " (" + String.join(", ", destCols) + ") " +
                    "SELECT " + String.join(", ", srcCols) + " FROM " + quote(oldPhysicalName);
            db.execute(sql);
        }

        db.execute("DROP TABLE " + quote(oldPhysicalName));
        db.execute("ALTER TABLE " + quote(tempName) + " RENAME TO " + quote(finalName));
    }

    private String buildCreateTableSql(String tableName, List<DatorColumn> columns) {
        List<DatorColumn> pkCols = new ArrayList<>();
        for (DatorColumn c : columns) {
            if (c.isPk()) {
                pkCols.add(c);
            }
        }
        boolean singleIntegerAutoPk = pkCols.size() == 1
                && "INTEGER".equalsIgnoreCase(pkCols.get(0).getDataType());

        List<String> defs = new ArrayList<>();
        for (DatorColumn c : columns) {
            StringBuilder cd = new StringBuilder();
            cd.append(quote(c.getName())).append(' ').append(sqlTypeFor(c.getDataType()));
            if (singleIntegerAutoPk && c.isPk()) {
                cd.append(" PRIMARY KEY AUTOINCREMENT");
            } else {
                if (c.isPk() || !c.isNullable()) {
                    cd.append(" NOT NULL");
                }
                if (c.isUnique() && !c.isPk()) {
                    cd.append(" UNIQUE");
                }
            }
            if (c.getDefaultValue() != null && !c.getDefaultValue().isEmpty()) {
                cd.append(" DEFAULT ").append(formatDefault(c.getDataType(), c.getDefaultValue()));
            }
            defs.add(cd.toString());
        }

        if (!singleIntegerAutoPk && !pkCols.isEmpty()) {
            List<String> pkNames = new ArrayList<>();
            for (DatorColumn c : pkCols) {
                pkNames.add(quote(c.getName()));
            }
            defs.add("PRIMARY KEY (" + String.join(", ", pkNames) + ")");
        }

        return "CREATE TABLE " + quote(tableName) + " (\n  " + String.join(",\n  ", defs) + "\n)";
    }

    /** LONGTEXT is a meta-only distinction (renders as a multiline editor); SQLite just sees TEXT. */
    private String sqlTypeFor(String dataType) {
        String type = dataType.toUpperCase(Locale.ROOT);
        return "LONGTEXT".equals(type) ? "TEXT" : type;
    }

    private String formatDefault(String dataType, String value) {
        String type = dataType.toUpperCase(Locale.ROOT);
        if (type.equals("INTEGER") || type.equals("REAL") || type.equals("NUMERIC")) {
            try {
                Double.parseDouble(value);
                return value;
            } catch (NumberFormatException e) {
                // fall through to quoted literal
            }
        }
        return "'" + value.replace("'", "''") + "'";
    }

    // ------------------------------------------------------------------
    // Writing: relations
    // ------------------------------------------------------------------

    /** Replaces the full set of relations owned by this table. */
    public void replaceRelations(int tableId, List<DatorRelation> relations) throws SQLException {
        Connection conn = db.getConnection();
        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM dator_table_relations WHERE table_id=?")) {
                ps.setInt(1, tableId);
                ps.executeUpdate();
            }
            for (DatorRelation r : relations) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO dator_table_relations(table_id, column_id, ref_table_id, " +
                                "ref_column_id, relation_type, display_mode, label) VALUES (?,?,?,?,?,?,?)")) {
                    ps.setInt(1, tableId);
                    ps.setInt(2, r.getColumnId());
                    ps.setInt(3, r.getRefTableId());
                    if (r.getRefColumnId() == null) {
                        ps.setNull(4, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(4, r.getRefColumnId());
                    }
                    ps.setString(5, r.getRelationType());
                    ps.setString(6, r.getDisplayMode() == null ? DatorRelation.DISPLAY_TAB : r.getDisplayMode());
                    ps.setString(7, r.getLabel());
                    ps.executeUpdate();
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

    // ------------------------------------------------------------------
    // Writing: summaries
    // ------------------------------------------------------------------

    /** Replaces the full set of summary rows owned by this table. */
    public void replaceSummaries(int tableId, List<DatorSummary> summaries) throws SQLException {
        Connection conn = db.getConnection();
        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM dator_table_summaries WHERE table_id=?")) {
                ps.setInt(1, tableId);
                ps.executeUpdate();
            }
            for (DatorSummary s : summaries) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO dator_table_summaries(table_id, column_id, aggregate, position, label) " +
                                "VALUES (?,?,?,?,?)")) {
                    ps.setInt(1, tableId);
                    ps.setInt(2, s.getColumnId());
                    ps.setString(3, s.getAggregate());
                    ps.setString(4, s.getPosition());
                    ps.setString(5, s.getLabel());
                    ps.executeUpdate();
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
}
