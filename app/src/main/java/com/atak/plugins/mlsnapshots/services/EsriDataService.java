package com.atak.plugins.mlsnapshots.services;

import com.atakmap.coremap.log.Log;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class EsriDataService {

    private static final String TAG = "EsriDataService";
    private final DuckDBService duckDBService;

    public EsriDataService(DuckDBService duckDBService) {
        this.duckDBService = duckDBService;
    }

    /**
     * Downloads an ESRI FeatureServer Layer and saves it as a GeoPackage.
     * This method handles pagination to efficiently download large datasets.
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

            int totalCount = getFeatureCount(conn, layerUrl);
            Log.d(TAG, "Total features to download: " + totalCount);

            if (totalCount == 0) {
                Log.w(TAG, "No features found for layer: " + layerUrl);
                return;
            }

            String initQuery = String.format("%s/query?where=1%%3D1&outFields=*&f=geojson&resultRecordCount=1", layerUrl);
            stmt.execute(String.format("CREATE TABLE %s AS SELECT * FROM ST_Read('%s');", tableName, initQuery));
            stmt.execute(String.format("DELETE FROM %s", tableName));

            int offset = 0;
            int limit = 1000;

            while (offset < totalCount) {
                String pageQueryUrl = String.format(
                    "%s/query?where=1%%3D1&outFields=*&f=geojson&resultOffset=%d&resultRecordCount=%d", 
                    layerUrl, offset, limit
                );
                
                Log.d(TAG, "Downloading batch: Offset " + offset);
                stmt.execute(String.format("INSERT INTO %s SELECT * FROM ST_Read('%s');", tableName, pageQueryUrl));
                offset += limit;
            }

            Log.d(TAG, "Download complete. Exporting to GeoPackage: " + outputPath);
            stmt.execute(String.format("COPY %s TO '%s' (FORMAT GDAL, DRIVER 'GPKG');", tableName, outputPath));
            Log.d(TAG, "Export successful to " + outputPath);
            stmt.execute("DROP TABLE IF EXISTS " + tableName);

        } catch (SQLException e) {
            Log.e(TAG, "Failed to download and export FeatureServer layer", e);
        }
    }

    /**
     * Downloads a region from an ESRI ImageServer as a single GeoTIFF.
     * Uses the exportImage REST API.
     */
    public void downloadImageServerRegion(String serviceUrl, double minLon, double minLat, double maxLon, double maxLat, String renderingRule, String outputPath) {
        String bbox = String.format("%f,%f,%f,%f", minLon, minLat, maxLon, maxLat);
        StringBuilder urlBuilder = new StringBuilder(serviceUrl);
        if (!serviceUrl.endsWith("/")) urlBuilder.append("/");
        urlBuilder.append("exportImage?f=image&format=tiff&bbox=").append(bbox).append("&bboxSR=4326&imageSR=4326");
        
        if (renderingRule != null && !renderingRule.isEmpty()) {
            urlBuilder.append("&renderingRule=").append(renderingRule);
        }

        String downloadUrl = urlBuilder.toString();
        Log.d(TAG, "Downloading GeoTIFF from: " + downloadUrl);

        try {
            URL url = new URL(downloadUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);

            if (connection.getResponseCode() == 200) {
                try (InputStream in = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream out = new FileOutputStream(outputPath)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    Log.d(TAG, "GeoTIFF saved to: " + outputPath);
                }
            } else {
                Log.e(TAG, "Server returned HTTP " + connection.getResponseCode());
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to download GeoTIFF", e);
        }
    }

    private static class TileTask {
        int z, x, y;
        byte[] data;
        boolean success;
        
        TileTask(int z, int x, int y, byte[] data, boolean success) {
            this.z = z; this.x = x; this.y = y; this.data = data; this.success = success;
        }
    }

    /**
     * Downloads a region from an ESRI ImageServer as tiles (XYZ) into a GeoPackage.
     * Uses the exportImage REST API to generate tiles dynamically.
     */
    public void downloadImageServerTiles(String serviceUrl, double minLon, double minLat, double maxLon, double maxLat, int minZoom, int maxZoom, String renderingRule, String outputPath) {
        if (duckDBService == null) return;

        Log.d(TAG, "Starting Tile Download to " + outputPath);
        
        try (Connection conn = duckDBService.getConnection()) {
            // 1. Initialize GeoPackage Tables
            initializeGeoPackageTiles(conn, outputPath, minLon, minLat, maxLon, maxLat, minZoom, maxZoom);

            // 2. Setup Producer-Consumer
            BlockingQueue<TileTask> queue = new LinkedBlockingQueue<>();
            ExecutorService executor = Executors.newFixedThreadPool(4);
            AtomicInteger totalTasks = new AtomicInteger(0);
            
            for (int z = minZoom; z <= maxZoom; z++) {
                int minX = lonToTileX(minLon, z);
                int maxX = lonToTileX(maxLon, z);
                int minY = latToTileY(maxLat, z);
                int maxY = latToTileY(minLat, z);

                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        final int zoom = z;
                        final int col = x;
                        final int row = y;
                        totalTasks.incrementAndGet();
                        
                        executor.submit(() -> {
                            try {
                                byte[] tileData = fetchTile(serviceUrl, zoom, col, row, renderingRule);
                                queue.offer(new TileTask(zoom, col, row, tileData, tileData != null));
                            } catch (Exception e) {
                                queue.offer(new TileTask(zoom, col, row, null, false));
                            }
                        });
                    }
                }
            }
            
            // 3. Consume and Insert
            int processed = 0;
            int successCount = 0;
            int failCount = 0;
            
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO gpkg.tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)")) {
                conn.setAutoCommit(false); // Begin Transaction
                
                int batchSize = 0;
                while (processed < totalTasks.get()) {
                    try {
                        TileTask task = queue.poll(5, TimeUnit.SECONDS);
                        if (task == null) continue; // Should not happen often if tasks are plenty

                        processed++;
                        if (task.success) {
                            ps.setInt(1, task.z);
                            ps.setInt(2, task.x);
                            ps.setInt(3, task.y);
                            ps.setBytes(4, task.data);
                            ps.addBatch();
                            batchSize++;
                            successCount++;
                        } else {
                            failCount++;
                        }

                        if (batchSize >= 50) {
                            ps.executeBatch();
                            conn.commit();
                            batchSize = 0;
                            Log.d(TAG, "Committed batch. Progress: " + processed + "/" + totalTasks.get());
                        }
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Consumer interrupted", e);
                        break;
                    }
                }
                
                if (batchSize > 0) {
                    ps.executeBatch();
                    conn.commit();
                }
                conn.setAutoCommit(true);
                
            } catch (SQLException e) {
                Log.e(TAG, "Error inserting tiles", e);
            }

            executor.shutdown();
            Log.d(TAG, "Tile download finished. Success: " + successCount + ", Failed: " + failCount);

        } catch (SQLException e) {
            Log.e(TAG, "Failed to initialize GeoPackage for tiles", e);
        }
    }

    private void initializeGeoPackageTiles(Connection conn, String gpkgPath, double minLon, double minLat, double maxLon, double maxLat, int minZoom, int maxZoom) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("INSTALL sqlite;");
            stmt.execute("LOAD sqlite;");
            stmt.execute(String.format("ATTACH '%s' AS gpkg (TYPE SQLITE);", gpkgPath));
            
            // Standard GPKG Tables
            stmt.execute("CREATE TABLE IF NOT EXISTS gpkg.gpkg_contents (table_name TEXT, data_type TEXT, identifier TEXT, description TEXT, last_change DATETIME, min_x DOUBLE, min_y DOUBLE, max_x DOUBLE, max_y DOUBLE, srs_id INTEGER);");
            stmt.execute("CREATE TABLE IF NOT EXISTS gpkg.gpkg_spatial_ref_sys (srs_name TEXT, srs_id INTEGER, organization TEXT, organization_coordsys_id INTEGER, definition TEXT, description TEXT);");
            stmt.execute("CREATE TABLE IF NOT EXISTS gpkg.gpkg_tile_matrix (table_name TEXT, zoom_level INTEGER, matrix_width INTEGER, matrix_height INTEGER, tile_width INTEGER, tile_height INTEGER, pixel_x_size DOUBLE, pixel_y_size DOUBLE, CONSTRAINT pk_ttm PRIMARY KEY (table_name, zoom_level));");
            stmt.execute("CREATE TABLE IF NOT EXISTS gpkg.gpkg_tile_matrix_set (table_name TEXT, srs_id INTEGER, min_x DOUBLE, min_y DOUBLE, max_x DOUBLE, max_y DOUBLE);");
            
            // Populate CRS
            stmt.execute("INSERT OR IGNORE INTO gpkg.gpkg_spatial_ref_sys VALUES ('WGS 84 / Pseudo-Mercator', 3857, 'EPSG', 3857, 'PROJCS[\"WGS 84 / Pseudo-Mercator\",GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563,AUTHORITY[\"EPSG\",\"7030\"]],AUTHORITY[\"EPSG\",\"6326\"]],PRIMEM[\"Greenwich\",0,AUTHORITY[\"EPSG\",\"8901\"]],UNIT[\"degree\",0.0174532925199433,AUTHORITY[\"EPSG\",\"9122\"]],AUTHORITY[\"EPSG\",\"4326\"]],PROJECTION[\"Mercator_1SP\"],PARAMETER[\"central_meridian\",0],PARAMETER[\"scale_factor\",1],PARAMETER[\"false_easting\",0],PARAMETER[\"false_northing\",0],UNIT[\"metre\",1,AUTHORITY[\"EPSG\",\"9001\"]],AXIS[\"Easting\",EAST],AXIS[\"Northing\",NORTH],EXTENSION[\"PROJ4\",\"+proj=merc +a=6378137 +b=6378137 +lat_ts=0.0 +lon_0=0.0 +x_0=0.0 +y_0=0 +k=1.0 +units=m +nadgrids=@null +wktext  +no_defs\"],AUTHORITY[\"EPSG\",\"3857\"]]','Undefined');");
            
            String tileTable = "tiles";
            stmt.execute("CREATE TABLE IF NOT EXISTS gpkg." + tileTable + " (id INTEGER PRIMARY KEY AUTOINCREMENT, zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB, UNIQUE(zoom_level, tile_column, tile_row));");

            // Register contents and matrix set (EPSG:3857 world bounds)
            double worldMax = 20037508.3427892;
            stmt.execute(String.format("INSERT OR REPLACE INTO gpkg.gpkg_contents VALUES ('%s', 'tiles', '%s', 'ESRI Download', strftime('%%Y-%%m-%%dT%%H:%%M:%%fZ','now'), %f, %f, %f, %f, 3857);", tileTable, tileTable, minLon, minLat, maxLon, maxLat));
            stmt.execute(String.format("INSERT OR REPLACE INTO gpkg.gpkg_tile_matrix_set VALUES ('%s', 3857, -%f, -%f, %f, %f);", tileTable, worldMax, worldMax, worldMax, worldMax));

            // Populate gpkg_tile_matrix for each zoom level
            for (int z = minZoom; z <= maxZoom; z++) {
                int matrixSize = 1 << z;
                double pixelSize = (2 * worldMax) / (256 * matrixSize);
                stmt.execute(String.format("INSERT OR REPLACE INTO gpkg.gpkg_tile_matrix VALUES ('%s', %d, %d, %d, 256, 256, %f, %f);", 
                    tileTable, z, matrixSize, matrixSize, pixelSize, pixelSize));
            }
        }
    }

    private byte[] fetchTile(String serviceUrl, int z, int x, int y, String renderingRule) {
        double[] bounds = tileBounds(x, y, z);
        String bbox = String.format("%f,%f,%f,%f", bounds[0], bounds[1], bounds[2], bounds[3]);
        
        StringBuilder urlBuilder = new StringBuilder(serviceUrl);
        if (!serviceUrl.endsWith("/")) urlBuilder.append("/");
        
        urlBuilder.append("exportImage?f=image&format=png&size=256,256")
                  .append("&bbox=").append(bbox)
                  .append("&bboxSR=3857&imageSR=3857");

        if (renderingRule != null && !renderingRule.isEmpty()) {
            urlBuilder.append("&renderingRule=").append(renderingRule);
        }

        try {
            URL url = new URL(urlBuilder.toString());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            if (connection.getResponseCode() == 200) {
                try (InputStream in = new BufferedInputStream(connection.getInputStream())) {
                    return readAllBytes(in);
                }
            }
        } catch (IOException e) {
            // Log.w(TAG, "Failed to fetch tile " + urlBuilder.toString());
        }
        return null;
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    // --- Math Helpers ---
    private int lonToTileX(double lon, int zoom) {
        return (int) Math.floor((lon + 180.0) / 360.0 * (1 << zoom));
    }

    private int latToTileY(double lat, int zoom) {
        return (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));
    }

    private double tile2lon(int x, int z) {
        return x / Math.pow(2.0, z) * 360.0 - 180;
    }

    private double tile2lat(int y, int z) {
        double n = Math.PI - 2.0 * Math.PI * y / Math.pow(2.0, z);
        return Math.toDegrees(Math.atan(0.5 * (Math.exp(n) - Math.exp(-n))));
    }
    
    private double[] tileBounds(int x, int y, int z) {
        double minLon = tile2lon(x, z);
        double maxLon = tile2lon(x + 1, z);
        double minLat = tile2lat(y + 1, z);
        double maxLat = tile2lat(y, z);

        double minX = lonToWebMercatorX(minLon);
        double maxX = lonToWebMercatorX(maxLon);
        double minY = latToWebMercatorY(minLat);
        double maxY = latToWebMercatorY(maxLat);

        return new double[]{minX, minY, maxX, maxY};
    }

    private double lonToWebMercatorX(double lon) {
        return lon * 20037508.34 / 180;
    }

    private double latToWebMercatorY(double lat) {
        double y = Math.log(Math.tan((90 + lat) * Math.PI / 360)) / (Math.PI / 180);
        return y * 20037508.34 / 180;
    }

    private int getFeatureCount(Connection conn, String layerUrl) {
        String countUrl = layerUrl + "/query?where=1%3D1&returnCountOnly=true&f=json";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(String.format("SELECT count FROM read_json_auto('%s');", countUrl))) {
            if (rs.next()) return rs.getInt("count");
        } catch (SQLException e) {
            Log.e(TAG, "Error fetching feature count", e);
        }
        return 0;
    }

    public void addFeatureServerLayer(String layerName, String serviceUrl, String layerId, String refreshIntervalCron) {
        if (duckDBService == null) {
            Log.e(TAG, "DuckDBService is not initialized.");
            return;
        }

        try (Connection conn = duckDBService.getConnection();
             Statement stmt = conn.createStatement()) {

            String tableName = layerName.replaceAll("[^a-zA-Z0-9_]", "_");
            String queryUrl = String.format("%s/%s/query?where=1%%3D1&outFields=*&f=geojson", serviceUrl, layerId);

            stmt.execute(String.format(
                "CREATE OR REPLACE TABLE %s AS SELECT * FROM ST_Read('%s');",
                tableName, queryUrl
            ));
            Log.d(TAG, "Successfully created table " + tableName + " and loaded initial data.");
            
            String cronJobName = "refresh_" + tableName;
            String cronQuery = String.format(
                "CREATE OR REPLACE TABLE %s AS SELECT * FROM ST_Read('%s');",
                tableName, queryUrl
            );

            stmt.execute(String.format("DELETE FROM cron.job WHERE name = '%s'", cronJobName));
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
                 ResultSet rs = stmt.executeQuery("SELECT *, ST_AsKML(geom) AS kml_geom FROM " + tableName)) {

                int colCount = rs.getMetaData().getColumnCount();
                List<String> colNames = new ArrayList<>();
                for(int i=1; i<=colCount; i++) {
                    colNames.add(rs.getMetaData().getColumnName(i));
                }

                while (rs.next()) {
                    kml.append("      <Placemark>\n");
                    
                    String placemarkName = "Feature";
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
