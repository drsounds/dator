package se.spacify.dator.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the single JDBC connection to a SQLite database file.
 */
public class Database {

    private final String path;
    private final Connection connection;

    public Database(String path) throws SQLException {
        this.path = path;
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
    }

    public String getPath() {
        return path;
    }

    public Connection getConnection() {
        return connection;
    }

    public void execute(String sql) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }
    }

    public boolean tableExists(String name) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // best effort
        }
    }
}
