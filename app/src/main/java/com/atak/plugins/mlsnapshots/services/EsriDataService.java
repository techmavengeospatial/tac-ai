package com.atak.plugins.mlsnapshots.services;

import com.atakmap.coremap.log.Log;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class EsriDataService {

    private static final String TAG = "EsriDataService";
    private final DuckDBService duckDBService;

    public EsriDataService(DuckDBService duckDBService) {
        this.duckDBService = duckDBService;
    }

    /**
     * Downloads an ESRI FeatureServer Layer and saves it as a GeoPackage.
     * This method handles pagination to efficiently download large datasets.
     *
     * @param serviceUrl The base URL of the FeatureServer (e.g., ".../FeatureServer")
     * @param layerId    The ID of the layer to download (e.g., "0")
     * @param outputPath The full path where the GeoPackage should be saved (e.g., "/sdcard/atak/layers/output.gpkg")
     */
    public void downloadLayerToGeoPackage(String serviceUrl, String layerId, String outputPath) {
        if (duckDBService == null) {
            Log.e(TAG, "DuckDBService is not initialized.");
            return;
        }

        String tableName = "temp_esri_" + System.currentTimeMillis();
        String layerUrl = String.format("%s/%s", serviceUrl, layerId);

        try (Connection conn = duckDBService.getConnection();
             Statement stmt = conn.createStatement()) {

            Log.d(TAG, "Starting download for layer: " + layerUrl);

            // 1. Get total feature count to plan pagination
            int totalCount = getFeatureCount(conn, layerUrl);
            Log.d(TAG, "Total features to download: " + totalCount);

            if (totalCount == 0) {
                Log.w(TAG, "No features found for layer: " + layerUrl);
                return;
            }

            // 2. Create the table structure by fetching the first feature (limit 1)
            // We use a temporary table to accumulate results
            String initQuery = String.format("%s/query?where=1%%3D1&outFields=*&f=geojson&resultRecordCount=1", layerUrl);
            stmt.execute(String.format("CREATE TABLE %s AS SELECT * FROM ST_Read('%s');", tableName, initQuery));
            
            // Clear the dummy record, we will fetch everything cleanly
            stmt.execute(String.format("DELETE FROM %s", tableName));

            // 3. Paginate and insert data
            int offset = 0;
            int limit = 1000; // ESRI default limit is often 1000 or 2000

            while (offset < totalCount) {
                String pageQueryUrl = String.format(
                    "%s/query?where=1%%3D1&outFields=*&f=geojson&resultOffset=%d&resultRecordCount=%d", 
                    layerUrl, offset, limit
                );
                
                Log.d(TAG, "Downloading batch: Offset " + offset);
                
                // Insert batch into the table
                stmt.execute(String.format("INSERT INTO %s SELECT * FROM ST_Read('%s');", tableName, pageQueryUrl));
                
                offset += limit;
            }

            Log.d(TAG, "Download complete. Exporting to GeoPackage: " + outputPath);

            // 4. Export to GeoPackage using GDAL driver via DuckDB COPY
            // Note: DuckDB's spatial extension uses GDAL, so we can COPY to GPKG
            stmt.execute(String.format("COPY %s TO '%s' (FORMAT GDAL, DRIVER 'GPKG');", tableName, outputPath));

            Log.d(TAG, "Export successful to " + outputPath);

            // 5. Cleanup
            stmt.execute("DROP TABLE IF EXISTS " + tableName);

        } catch (SQLException e) {
            Log.e(TAG, "Failed to download and export FeatureServer layer", e);
        }
    }

    private int getFeatureCount(Connection conn, String layerUrl) {
        String countUrl = layerUrl + "/query?where=1%3D1&returnCountOnly=true&f=json";
        // ESRI returns JSON like {"count": 1234}
        // We can use DuckDB's read_json_auto or simply parse it. 
        // For robustness in SQL, we can try reading it as a JSON source.
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(String.format("SELECT count FROM read_json_auto('%s');", countUrl))) {
            
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            Log.e(TAG, "Error fetching feature count", e);
        }
        return 0;
    }

    /**
     * Adds a live FeatureServer layer to DuckDB, backed by a periodic refresh (cron).
     */
    public void addFeatureServerLayer(String layerName, String serviceUrl, String layerId, String refreshIntervalCron) {
        if (duckDBService == null) {
            Log.e(TAG, "DuckDBService is not initialized.");
            return;
        }

        try (Connection conn = duckDBService.getConnection();
             Statement stmt = conn.createStatement()) {

            String tableName = layerName.replaceAll("[^a-zA-Z0-9_]", "_");
            // Initial load URL (fetching everything might be heavy, consider a limit or bbox for live layers)
            String queryUrl = String.format("%s/%s/query?where=1%%3D1&outFields=*&f=geojson", serviceUrl, layerId);

            // Create table and load initial data
            stmt.execute(String.format(
                "CREATE OR REPLACE TABLE %s AS SELECT * FROM ST_Read('%s');",
                tableName, queryUrl
            ));
            Log.d(TAG, "Successfully created table " + tableName + " and loaded initial data.");
            
            // Setup Cron Job for refresh
            String cronJobName = "refresh_" + tableName;
            
            // Note: This query replaces the table content with fresh data from the URL
            String cronQuery = String.format(
                "CREATE OR REPLACE TABLE %s AS SELECT * FROM ST_Read('%s');",
                tableName, queryUrl
            );

            // Cleanup old job if exists
            stmt.execute(String.format("DELETE FROM cron.job WHERE name = '%s'", cronJobName));

            // Schedule new job
            // Using prepared statement logic for safety, though string format is used here for simplicity with the cron extension syntax
            stmt.execute(String.format(
                "INSERT INTO cron.job(name, schedule, command) VALUES ('%s', '%s', '%s');",
                cronJobName, refreshIntervalCron, cronQuery.replace("'", "''")
            ));

            Log.d(TAG, "Scheduled cron job '" + cronJobName + "'");

        } catch (SQLException e) {
            Log.e(TAG, "Failed to add Feature Server layer: " + layerName, e);
        }
    }

    public void addStreamServerLayer(String streamName, String streamUrl) {
        Log.d(TAG, "addStreamServerLayer is not yet implemented.");
    }

    public String getKmlForLayer(String layerName) {
        String tableName = layerName.replaceAll("[^a-zA-Z0-9_]", "_");
        StringBuilder kml = new StringBuilder();
        kml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        kml.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n");
        kml.append("  <Document>\n");
        kml.append("    <name>").append(layerName).append("</name>\n");

        if (duckDBService != null) {
            try (Connection conn = duckDBService.getConnection();
                 Statement stmt = conn.createStatement();
                 // ST_AsKML is a standard spatial function
                 ResultSet rs = stmt.executeQuery("SELECT *, ST_AsKML(geom) AS kml_geom FROM " + tableName)) {

                int colCount = rs.getMetaData().getColumnCount();
                List<String> colNames = new ArrayList<>();
                for(int i=1; i<=colCount; i++) {
                    colNames.add(rs.getMetaData().getColumnName(i));
                }

                while (rs.next()) {
                    kml.append("      <Placemark>\n");
                    
                    String placemarkName = "Feature";
                    // Simple heuristic for name
                    for (String col : colNames) {
                        if (col.equalsIgnoreCase("name") || col.equalsIgnoreCase("title") || col.equalsIgnoreCase("objectid")) {
                            String val = rs.getString(col);
                            if (val != null) {
                                placemarkName = val;
                                break;
                            }
                        }
                    }
                    kml.append("        <name>").append(placemarkName).append("</name>\n");
                    
                    StringBuilder description = new StringBuilder();
                    description.append("<![CDATA[<ul>");
                    for (int i = 1; i <= colCount; i++) {
                        String name = rs.getMetaData().getColumnName(i);
                        if (!name.equalsIgnoreCase("geom") && !name.equalsIgnoreCase("kml_geom")) {
                            Object value = rs.getObject(i);
                            description.append("<li><b>").append(name).append(":</b> ").append(value).append("</li>");
                        }
                    }
                    description.append("</ul>]]>");
                    kml.append("        <description>").append(description).append("</description>\n");

                    String kmlGeom = rs.getString("kml_geom");
                    if (kmlGeom != null) {
                        kml.append("        ").append(kmlGeom).append("\n");
                    }
                    kml.append("      </Placemark>\n");
                }
            } catch (SQLException e) {
                Log.e(TAG, "Failed to generate KML for layer: " + layerName, e);
            }
        }

        kml.append("  </Document>\n");
        kml.append("</kml>");
        return kml.toString();
    }
}
