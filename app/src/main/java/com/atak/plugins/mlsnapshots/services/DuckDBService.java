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
                // Core extensions
                installAndLoad(stmt, "spatial");
                installAndLoad(stmt, "httpfs");
                installAndLoad(stmt, "http_client");
                installAndLoad(stmt, "cron");

                // Community / Query.Farm extensions for Real-Time & Event-Driven Data
                // Note: These may require internet access to download and specific architecture support (e.g., android-aarch64)
                
                // Allow unsigned extensions (often needed for community extensions)
                stmt.execute("SET allow_unsigned_extensions = true;");

                // Configure Query.Farm repository
                // stmt.execute("SET custom_extension_repository = 'http://duckdb.query.farm';");

                // Real-time streaming (Kafka)
                installAndLoad(stmt, "tributary");
                
                // Event-driven messaging (WebSockets, Redis)
                installAndLoad(stmt, "radio");
                
                // Data gathering
                installAndLoad(stmt, "crawler");
                
                // Additional protocols
                installAndLoad(stmt, "webdavfs"); // WebDAV
                // installAndLoad(stmt, "sshfs"); // SSH - often requires libssh, might be tricky on Android without static linking
                
                // HTTP Request (if distinct from http_client)
                // installAndLoad(stmt, "http_request"); 

                // Attach extensions to the in-memory database
                stmt.execute("ATTACH ':memory:' AS mem");
                stmt.execute("USE mem");
            }
        } catch (ClassNotFoundException | SQLException e) {
            throw new SQLException("Failed to initialize DuckDBService", e);
        }
    }

    private void installAndLoad(Statement stmt, String extension) {
        try {
            Log.d(TAG, "Attempting to install and load: " + extension);
            stmt.execute("INSTALL " + extension + ";");
            stmt.execute("LOAD " + extension + ";");
            Log.d(TAG, "Successfully loaded: " + extension);
        } catch (SQLException e) {
            // Log but don't fail completely, as some extensions might not be available for this platform
            Log.w(TAG, "Failed to load extension: " + extension + ". It may not be supported on this platform or architecture.", e);
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
