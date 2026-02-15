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
                // Install and Load Extensions
                // Note: On Android, downloading extensions requires network and valid architecture config.
                // Assuming the bundled DuckDB version or online capability supports these.
                
                String[] extensions = {"spatial", "httpfs", "http_client", "cron", "zipfs"}; // Added zipfs
                
                for (String ext : extensions) {
                    try {
                        stmt.execute("INSTALL " + ext + ";");
                        stmt.execute("LOAD " + ext + ";");
                        Log.d(TAG, "Loaded extension: " + ext);
                    } catch (SQLException e) {
                        Log.w(TAG, "Could not load extension " + ext + ": " + e.getMessage());
                    }
                }

                // Attach in-memory database if strictly needed, though usually main is enough
                // stmt.execute("ATTACH ':memory:' AS mem");
                
                // Set S3 configuration for Overture Maps (No Auth needed for Overture)
                stmt.execute("SET s3_region='us-west-2';");
                
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
