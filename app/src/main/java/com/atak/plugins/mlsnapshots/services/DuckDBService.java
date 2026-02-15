
package com.atak.plugins.mlsnapshots.services;

import org.duckdb.DuckDBConnection;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DuckDBService {

    private Connection conn;

    public DuckDBService(String dbPath) throws SQLException {
        try {
            Class.forName("org.duckdb.DuckDBDriver");
            conn = DriverManager.getConnection("jdbc:duckdb:" + dbPath);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSTALL spatial;");
                stmt.execute("LOAD spatial;");
                stmt.execute("INSTALL tributary;");
                stmt.execute("LOAD tributary;");
                stmt.execute("INSTALL radio;");
                stmt.execute("LOAD radio;");
                stmt.execute("INSTALL webdavfs;");
                stmt.execute("LOAD webdavfs;");
                stmt.execute("INSTALL sshfs;");
                stmt.execute("LOAD sshfs;");
                stmt.execute("INSTALL http_request;");
                stmt.execute("LOAD http_request;");
                stmt.execute("INSTALL crawler;");
                stmt.execute("LOAD crawler;");
                stmt.execute("INSTALL cron;");
                stmt.execute("LOAD cron;");
                stmt.execute("INSTALL http_client;");
                stmt.execute("LOAD http_client;");
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
        }
    }
}
