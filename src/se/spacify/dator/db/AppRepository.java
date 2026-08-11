package se.spacify.dator.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import se.spacify.dator.model.DatorApp;
import se.spacify.dator.model.DatorFeature;

/**
 * Reads and writes the dator_apps / dator_features meta tables. An app is a
 * named group of features; a feature binds an ISPF Dialog Manager-style
 * panel (se.spacify.dator.panel) to a dator_tables model, with an optional
 * menu shortcut for jumping straight to it.
 */
public class AppRepository {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern SHORTCUT = Pattern.compile("^[A-Za-z0-9_.\\-]+$");

    private final Database db;

    public AppRepository(Database db) throws SQLException {
        this.db = db;
        ensureSchema();
    }

    private void ensureSchema() throws SQLException {
        db.execute("CREATE TABLE IF NOT EXISTS dator_apps (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL UNIQUE, " +
                "label TEXT, " +
                "description TEXT, " +
                "created_at TEXT NOT NULL DEFAULT (datetime('now')))");

        db.execute("CREATE TABLE IF NOT EXISTS dator_features (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "app_id INTEGER NOT NULL REFERENCES dator_apps(id) ON DELETE CASCADE, " +
                "table_id INTEGER NOT NULL REFERENCES dator_tables(id) ON DELETE CASCADE, " +
                "name TEXT NOT NULL, " +
                "label TEXT, " +
                "shortcut TEXT UNIQUE, " +
                "panel_source TEXT NOT NULL, " +
                "ordinal INTEGER NOT NULL DEFAULT 0, " +
                "created_at TEXT NOT NULL DEFAULT (datetime('now')), " +
                "UNIQUE(app_id, name))");
    }

    // ------------------------------------------------------------------
    // Validation helpers
    // ------------------------------------------------------------------

    public static void validateName(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("\"" + name + "\" is not a valid identifier " +
                    "(letters, digits, underscore; must not start with a digit)");
        }
    }

    public static void validateShortcut(String shortcut) {
        if (shortcut == null || shortcut.isEmpty()) {
            return;
        }
        if (!SHORTCUT.matcher(shortcut).matches()) {
            throw new IllegalArgumentException("\"" + shortcut + "\" is not a valid shortcut " +
                    "(letters, digits, dot, underscore, hyphen)");
        }
    }

    // ------------------------------------------------------------------
    // Apps
    // ------------------------------------------------------------------

    public List<DatorApp> listApps() throws SQLException {
        List<DatorApp> result = new ArrayList<>();
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id, name, label, description FROM dator_apps ORDER BY name")) {
            while (rs.next()) {
                result.add(readApp(rs));
            }
        }
        return result;
    }

    public DatorApp getApp(int id) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT id, name, label, description FROM dator_apps WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readApp(rs);
                }
            }
        }
        return null;
    }

    public DatorApp findAppByName(String name) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT id, name, label, description FROM dator_apps WHERE name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readApp(rs);
                }
            }
        }
        return null;
    }

    private DatorApp readApp(ResultSet rs) throws SQLException {
        return new DatorApp(rs.getInt("id"), rs.getString("name"), rs.getString("label"),
                rs.getString("description"));
    }

    public DatorApp saveApp(DatorApp app) throws SQLException {
        validateName(app.getName());
        if (app.getId() <= 0) {
            DatorApp existing = findAppByName(app.getName());
            if (existing != null) {
                throw new IllegalArgumentException("An app named \"" + app.getName() + "\" already exists");
            }
            try (PreparedStatement ps = db.getConnection().prepareStatement(
                    "INSERT INTO dator_apps(name, label, description) VALUES (?,?,?)")) {
                ps.setString(1, app.getName());
                ps.setString(2, app.getLabel());
                ps.setString(3, app.getDescription());
                ps.executeUpdate();
            }
            try (Statement st = db.getConnection().createStatement();
                 ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                rs.next();
                app.setId(rs.getInt(1));
            }
        } else {
            try (PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE dator_apps SET name=?, label=?, description=? WHERE id=?")) {
                ps.setString(1, app.getName());
                ps.setString(2, app.getLabel());
                ps.setString(3, app.getDescription());
                ps.setInt(4, app.getId());
                ps.executeUpdate();
            }
        }
        return app;
    }

    public void deleteApp(DatorApp app) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM dator_apps WHERE id=?")) {
            ps.setInt(1, app.getId());
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // Features
    // ------------------------------------------------------------------

    public List<DatorFeature> listFeatures(int appId) throws SQLException {
        List<DatorFeature> result = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT id, app_id, table_id, name, label, shortcut, panel_source, ordinal " +
                        "FROM dator_features WHERE app_id=? ORDER BY ordinal, id")) {
            ps.setInt(1, appId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(readFeature(rs));
                }
            }
        }
        return result;
    }

    public List<DatorFeature> listAllFeatures() throws SQLException {
        List<DatorFeature> result = new ArrayList<>();
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id, app_id, table_id, name, label, shortcut, panel_source, ordinal " +
                             "FROM dator_features ORDER BY app_id, ordinal, id")) {
            while (rs.next()) {
                result.add(readFeature(rs));
            }
        }
        return result;
    }

    public DatorFeature getFeature(int id) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT id, app_id, table_id, name, label, shortcut, panel_source, ordinal " +
                        "FROM dator_features WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readFeature(rs);
                }
            }
        }
        return null;
    }

    public DatorFeature findFeatureByShortcut(String shortcut) throws SQLException {
        if (shortcut == null || shortcut.isEmpty()) {
            return null;
        }
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT id, app_id, table_id, name, label, shortcut, panel_source, ordinal " +
                        "FROM dator_features WHERE UPPER(shortcut)=UPPER(?)")) {
            ps.setString(1, shortcut);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readFeature(rs);
                }
            }
        }
        return null;
    }

    private DatorFeature readFeature(ResultSet rs) throws SQLException {
        DatorFeature f = new DatorFeature();
        f.setId(rs.getInt("id"));
        f.setAppId(rs.getInt("app_id"));
        f.setTableId(rs.getInt("table_id"));
        f.setName(rs.getString("name"));
        f.setLabel(rs.getString("label"));
        f.setShortcut(rs.getString("shortcut"));
        f.setPanelSource(rs.getString("panel_source"));
        f.setOrdinal(rs.getInt("ordinal"));
        return f;
    }

    public DatorFeature saveFeature(DatorFeature feature) throws SQLException {
        validateName(feature.getName());
        validateShortcut(feature.getShortcut());
        if (feature.getPanelSource() == null || feature.getPanelSource().trim().isEmpty()) {
            throw new IllegalArgumentException("Panel source is required");
        }
        if (feature.getShortcut() != null && !feature.getShortcut().isEmpty()) {
            DatorFeature existing = findFeatureByShortcut(feature.getShortcut());
            if (existing != null && existing.getId() != feature.getId()) {
                throw new IllegalArgumentException(
                        "Shortcut \"" + feature.getShortcut() + "\" is already used by another feature");
            }
        }

        Connection conn = db.getConnection();
        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            if (feature.getId() <= 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO dator_features(app_id, table_id, name, label, shortcut, " +
                                "panel_source, ordinal) VALUES (?,?,?,?,?,?,?)")) {
                    ps.setInt(1, feature.getAppId());
                    ps.setInt(2, feature.getTableId());
                    ps.setString(3, feature.getName());
                    ps.setString(4, feature.getLabel());
                    ps.setString(5, emptyToNull(feature.getShortcut()));
                    ps.setString(6, feature.getPanelSource());
                    ps.setInt(7, feature.getOrdinal());
                    ps.executeUpdate();
                }
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                    rs.next();
                    feature.setId(rs.getInt(1));
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE dator_features SET app_id=?, table_id=?, name=?, label=?, shortcut=?, " +
                                "panel_source=?, ordinal=? WHERE id=?")) {
                    ps.setInt(1, feature.getAppId());
                    ps.setInt(2, feature.getTableId());
                    ps.setString(3, feature.getName());
                    ps.setString(4, feature.getLabel());
                    ps.setString(5, emptyToNull(feature.getShortcut()));
                    ps.setString(6, feature.getPanelSource());
                    ps.setInt(7, feature.getOrdinal());
                    ps.setInt(8, feature.getId());
                    ps.executeUpdate();
                }
            }
            conn.commit();
            return feature;
        } catch (RuntimeException | SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }

    public void deleteFeature(DatorFeature feature) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM dator_features WHERE id=?")) {
            ps.setInt(1, feature.getId());
            ps.executeUpdate();
        }
    }

    private String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
