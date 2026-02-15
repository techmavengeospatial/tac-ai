package com.atak.plugins.mlsnapshots.services;

import com.atak.plugins.mlsnapshots.PluginMapComponent;
import com.atakmap.coremap.log.Log;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DuckDBService {

    private static final String TAG = "DuckDBService";
    private Connection conn;

    public DuckDBService(String dbPath) throws SQLException {
        try {
            Class.forName("org.duckdb.DuckDBDriver");
            conn = DriverManager.getConnection("jdbc:duckdb:" + dbPath);
            Log.d(TAG, "DuckDB connection established to: " + dbPath);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSTALL spatial;");
                stmt.execute("LOAD spatial;");
                stmt.execute("INSTALL httpfs;");
                stmt.execute("LOAD httpfs;");
                stmt.execute("INSTALL http_client;");
                stmt.execute("LOAD http_client;");
                stmt.execute("INSTALL cron;");
                stmt.execute("LOAD cron;");

                // Attach extensions to the in-memory database
                stmt.execute("ATTACH ':memory:' AS mem");
                stmt.execute("USE mem");

                // Verify loaded extensions
                // ResultSet rs = stmt.executeQuery("SELECT * FROM duckdb_extensions();");
                // while (rs.next()) {
                //     Log.d(TAG, "Extension: " + rs.getString("extension_name") + ", Loaded: " + rs.getBoolean("loaded"));
                // }
            }
        } catch (ClassNotFoundException | SQLException e) {
            throw new SQLException("Failed to initialize DuckDBService", e);
        }
    }

    public Connection getConnection() {
        return conn;
    }

    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
            Log.d(TAG, "DuckDB connection closed.");
        }
    }
}
