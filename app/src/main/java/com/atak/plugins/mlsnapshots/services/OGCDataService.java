package com.atak.plugins.mlsnapshots.services;

import com.atakmap.coremap.log.Log;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class OGCDataService {

    private static final String TAG = "OGCDataService";
    private final DuckDBService duckDBService;

    public OGCDataService(DuckDBService duckDBService) {
        this.duckDBService = duckDBService;
    }

    // ... (Existing methods for SensorThings, MovingFeatures, GTFS, SOS remain same) ...
    public void addSensorThingsFeed(String name, String url, String intervalCron) {
        if (duckDBService == null) return;
        String tableName = "st_" + name.replaceAll("[^a-zA-Z0-9_]", "_");
        String queryUrl = url + "?$filter=orderby%20phenomenonTime%20desc&$top=100";
        String createTableSql = String.format("CREATE OR REPLACE TABLE %s AS SELECT * FROM read_json_auto('%s');", tableName, queryUrl);
        scheduleCronJob(name, intervalCron, createTableSql);
    }
    
    public void addMovingFeaturesFeed(String name, String url, String intervalCron) {
        if (duckDBService == null) return;
        String tableName = "mf_" + name.replaceAll("[^a-zA-Z0-9_]", "_");
        String createTableSql = String.format("CREATE OR REPLACE TABLE %s AS SELECT * FROM read_json_auto('%s');", tableName, url);
        scheduleCronJob(name, intervalCron, createTableSql);
    }
    
    public void addGtfsRealtimeFeed(String name, String url, String intervalCron) {
        if (duckDBService == null) return;
        String tableName = "gtfs_" + name.replaceAll("[^a-zA-Z0-9_]", "_");
        String createTableSql = String.format("CREATE OR REPLACE TABLE %s AS SELECT * FROM read_json_auto('%s');", tableName, url);
        scheduleCronJob(name, intervalCron, createTableSql);
    }
    
    public void addSOSFeed(String name, String url, String intervalCron) {
        if (duckDBService == null) return;
        String tableName = "sos_" + name.replaceAll("[^a-zA-Z0-9_]", "_");
        String jsonUrl = url + (url.contains("?") ? "&" : "?") + "service=SOS&version=2.0.0&request=GetObservation&responseFormat=application%2Fjson";
        String createTableSql = String.format("CREATE OR REPLACE TABLE %s AS SELECT * FROM read_json_auto('%s');", tableName, jsonUrl);
        scheduleCronJob(name, intervalCron, createTableSql);
    }

    /**
     * Adds a Redis feed.
     * Uses the DuckDB 'redis' extension.
     * Can be a Key-Value scan (polling) or potentially Pub/Sub if supported by extension/radio.
     * 
     * @param name Feed name
     * @param connectionString Redis connection string (redis://user:password@host:port)
     * @param keyPattern Key pattern to scan (e.g., "sensor:*")
     * @param intervalCron Polling interval
     */
    public void addRedisFeed(String name, String connectionString, String keyPattern, String intervalCron) {
        if (duckDBService == null) return;
        
        String tableName = "redis_" + name.replaceAll("[^a-zA-Z0-9_]", "_");
        
        // Ensure Redis extension is loaded (DuckDBService should handle this centrally, but we double check or assume)
        // Note: The 'redis' extension allows querying keys and values.
        // Example: SELECT * FROM redis_scan('redis://...', 'sensor:*')
        
        String createTableSql = String.format(
            "CREATE OR REPLACE TABLE %s AS SELECT * FROM redis_scan('%s', '%s');", 
            tableName, connectionString, keyPattern
        );
        
        // If we want to transform the data (e.g. value is JSON), we can do:
        // CREATE TABLE ... AS SELECT key, from_json(value, '{"lat":"DOUBLE", "lon":"DOUBLE", ...}') ...
        // For generic feed, we just dump the KV pairs.
        
        scheduleCronJob(name, intervalCron, createTableSql);
    }

    /**
     * Adds a Redis Pub/Sub feed using the 'radio' extension (if applicable) or Redis extension stream features.
     * Note: This is typically push-based and might not fit the 'cron' model perfectly, 
     * but 'radio' extension can buffer messages into a table which we then query.
     */
    public void addRedisPubSubFeed(String name, String connectionString, String channel) {
        if (duckDBService == null) return;
        
        // This requires the 'radio' extension or similar event-driven capability.
        // If using 'radio', we might configure it to listen and push to a table.
        // Since we don't have a direct 'addRadioListener' API exposed yet in DuckDBService, 
        // we'll assume a SQL command can set this up.
        
        try (Connection conn = duckDBService.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Example hypothetical syntax for Radio/Redis PubSub
            // INSTALL radio; LOAD radio;
            // CREATE TABLE my_stream (...);
            // COPY (SELECT * FROM redis_subscribe('...')) TO my_stream; -- likely runs as a background task or blocking
            
            Log.d(TAG, "Redis Pub/Sub setup requires specific extension commands. Placeholder.");
            
        } catch (SQLException e) {
            Log.e(TAG, "Failed to setup Redis Pub/Sub", e);
        }
    }

    private void scheduleCronJob(String name, String interval, String sql) {
        try (Connection conn = duckDBService.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Initial run
            try {
                stmt.execute(sql);
                Log.d(TAG, "Initial fetch successful for " + name);
            } catch (SQLException e) {
                Log.e(TAG, "Initial fetch failed for " + name + ". Check connection/format.", e);
            }

            String cronJobName = "job_" + name.replaceAll("[^a-zA-Z0-9_]", "_");
            stmt.execute(String.format("DELETE FROM cron.job WHERE name = '%s'", cronJobName));
            String escapedSql = sql.replace("'", "''");
            stmt.execute(String.format(
                "INSERT INTO cron.job(name, schedule, command) VALUES ('%s', '%s', '%s');",
                cronJobName, interval, escapedSql
            ));
            Log.d(TAG, "Scheduled job " + cronJobName);

        } catch (SQLException e) {
            Log.e(TAG, "Failed to schedule job for " + name, e);
        }
    }
}
