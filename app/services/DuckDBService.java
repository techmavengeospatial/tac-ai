package com.example.atak.services;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Service
public class DuckDBService {

    private Connection conn;

    @FunctionalInterface
    public interface ResultSetExtractor<T> {
        T extractData(ResultSet rs) throws SQLException;
    }

    @PostConstruct
    public void initialize() {
        try {
            Class.forName("org.duckdb.DuckDBDriver");
            conn = DriverManager.getConnection("jdbc:duckdb:");
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSTALL httpfs");
                stmt.execute("LOAD httpfs");
                stmt.execute("INSTALL json");
                stmt.execute("LOAD json");
                stmt.execute("INSTALL cron");
                stmt.execute("LOAD cron");
                stmt.execute("INSTALL radio");
                stmt.execute("LOAD radio");
                stmt.execute("INSTALL http_request");
                stmt.execute("LOAD http_request");
                stmt.execute("INSTALL crawler");
                stmt.execute("LOAD crawler");
            }
        } catch (ClassNotFoundException | SQLException e) {
            throw new RuntimeException("Failed to initialize DuckDB service", e);
        }
    }

    public void execute(String sql) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute SQL statement", e);
        }
    }

    public <T> T query(String sql, ResultSetExtractor<T> extractor) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return extractor.extractData(rs);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute query", e);
        }
    }

    public void startPolling(String url, int intervalSeconds) {
        String cron_expr = "'@every " + intervalSeconds + "s'";

        String createTableFirst = String.format("CREATE OR REPLACE TABLE polled_data AS SELECT * FROM read_json_auto('%s');", url);
        execute(createTableFirst);
        
        String pollingJob = String.format(
            "CREATE OR REPLACE CRON http_poll_job %s AS INSERT INTO polled_data SELECT * FROM read_json_auto('%s');",
            cron_expr,
            url
        );

        execute(pollingJob);
    }

    @PreDestroy
    public void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to close DuckDB connection", e);
        }
    }
}
