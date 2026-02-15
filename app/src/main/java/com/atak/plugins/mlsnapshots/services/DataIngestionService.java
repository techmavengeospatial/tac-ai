
package com.atak.plugins.mlsnapshots.services;

import com.atak.plugins.mlsnapshots.services.DuckDBService;
import java.sql.SQLException;
import java.sql.Statement;

public class DataIngestionService {

    private final DuckDBService duckDBService;

    public DataIngestionService(DuckDBService duckDBService) {
        this.duckDBService = duckDBService;
    }

    public void start() throws SQLException {
        try (Statement stmt = duckDBService.getConnection().createStatement()) {
            // Create a table to store the events
            stmt.execute("CREATE TABLE IF NOT EXISTS events (id INTEGER, lat DOUBLE, lon DOUBLE, timestamp VARCHAR, name VARCHAR, geom GEOMETRY);");

            // Use cron to periodically fetch data
            // This is a simplified example. In a real-world scenario, you would fetch from a live endpoint.
            stmt.execute("CREATE CRON JOB 'ingest_data' AS \"INSERT INTO events SELECT id, lat, lon, timestamp, name, ST_Point(lon, lat) FROM read_json_auto('app/src/main/resources/static/events.json')\"");
        }
    }

    public void stop() throws SQLException {
        try (Statement stmt = duckDBService.getConnection().createStatement()) {
            stmt.execute("DROP CRON JOB IF EXISTS ingest_data;");
        }
    }
}
