
package com.atak.plugins.mlsnapshots.services;

import com.atak.coremap.log.Log;
import com.atak.plugins.mlsnapshots.PluginExecutor;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class OpenAddressesService {

    public static final String TAG = "OpenAddressesService";
    private final DuckDBService duckDBService;
    private final Executor executor = PluginExecutor.getExecutor();
    private final File dataDir;

    public interface ResultListener {
        void onSuccess(String message);
        void onError(String error);
    }
    
    public interface GeocodeListener {
        void onResults(List<Map<String, Object>> results);
        void onError(String error);
    }

    public OpenAddressesService(DuckDBService duckDBService, File dataDir) {
        this.duckDBService = duckDBService;
        this.dataDir = dataDir;
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    public void downloadAndIngest(String urlString, String tableName, ResultListener listener) {
        executor.execute(() -> {
            try {
                listener.onSuccess("Starting download...");
                File zipFile = downloadFile(urlString);
                
                listener.onSuccess("Extracting...");
                List<File> extractedFiles = unzip(zipFile);
                
                listener.onSuccess("Ingesting into DuckDB...");
                ingestFiles(extractedFiles, tableName);
                
                // Cleanup
                zipFile.delete();
                for(File f : extractedFiles) {
                    f.delete();
                }

                listener.onSuccess("Ingestion complete. Table: " + tableName);
            } catch (Exception e) {
                Log.e(TAG, "Error processing OpenAddresses data", e);
                listener.onError("Error: " + e.getMessage());
            }
        });
    }

    private File downloadFile(String urlString) throws IOException {
        URL url = new URL(urlString);
        File destFile = new File(dataDir, "oa_temp.zip");
        try (BufferedInputStream in = new BufferedInputStream(url.openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(destFile)) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        }
        return destFile;
    }

    private List<File> unzip(File zipFile) throws IOException {
        List<File> extractedFiles = new ArrayList<>();
        try (ZipFile zf = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                
                // We only care about CSV or Shapefiles for now
                String name = entry.getName().toLowerCase();
                if (name.endsWith(".csv") || name.endsWith(".shp")) {
                    File destFile = new File(dataDir, new File(entry.getName()).getName());
                    try (BufferedInputStream bis = new BufferedInputStream(zf.getInputStream(entry));
                         FileOutputStream fos = new FileOutputStream(destFile)) {
                        byte[] buffer = new byte[2048];
                        int count;
                        while ((count = bis.read(buffer)) != -1) {
                            fos.write(buffer, 0, count);
                        }
                    }
                    extractedFiles.add(destFile);
                }
            }
        }
        return extractedFiles;
    }

    private void ingestFiles(List<File> files, String tableName) throws SQLException {
        try (Connection conn = duckDBService.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Drop table if exists to start fresh (or append if you prefer)
            stmt.execute("DROP TABLE IF EXISTS " + tableName);

            for (File file : files) {
                String path = file.getAbsolutePath();
                // Check extension
                if (path.endsWith(".csv")) {
                    // OpenAddresses CSVs usually define columns. 
                    // We use read_csv_auto to infer types.
                    // We create the table from the first file, then insert for subsequent ones
                    // BUT OpenAddresses schemas can vary slightly between regions.
                    // For simplicity, we assume we are loading one region or compatible regions.
                    
                    // Logic: If table doesn't exist, create it. Else insert.
                    boolean tableExists = checkTableExists(conn, tableName);
                    
                    if (!tableExists) {
                        String sql = String.format("CREATE TABLE %s AS SELECT * FROM read_csv_auto('%s');", tableName, path);
                        stmt.execute(sql);
                    } else {
                        // Attempt to append. This might fail if schemas mismatch.
                        // In a robust app, we'd unify schemas.
                        try {
                            String sql = String.format("INSERT INTO %s SELECT * FROM read_csv_auto('%s');", tableName, path);
                            stmt.execute(sql);
                        } catch (SQLException e) {
                            Log.w(TAG, "Skipping incompatible file: " + file.getName() + " - " + e.getMessage());
                        }
                    }
                } else if (path.endsWith(".shp")) {
                    // Similar logic for Shapefiles using ST_Read
                     boolean tableExists = checkTableExists(conn, tableName);
                     if (!tableExists) {
                         String sql = String.format("CREATE TABLE %s AS SELECT * FROM ST_Read('%s');", tableName, path);
                         stmt.execute(sql);
                     } else {
                         String sql = String.format("INSERT INTO %s SELECT * FROM ST_Read('%s');", tableName, path);
                         stmt.execute(sql);
                     }
                }
            }
            
            // Post-processing: Ensure we have a geometry column if it was a CSV with LAT/LON
            // OpenAddresses CSV typically has LON and LAT columns
            if (checkColumnExists(conn, tableName, "LON") && checkColumnExists(conn, tableName, "LAT")) {
                 try {
                     stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN IF NOT EXISTS geom GEOMETRY");
                     stmt.execute("UPDATE " + tableName + " SET geom = ST_Point(LON, LAT) WHERE geom IS NULL");
                 } catch (SQLException e) {
                     Log.w(TAG, "Could not create geometry column: " + e.getMessage());
                 }
            }
        }
    }
    
    private boolean checkTableExists(Connection conn, String tableName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    private boolean checkColumnExists(Connection conn, String tableName, String colName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, colName)) {
            return rs.next();
        }
    }

    public void geocode(String tableName, String query, GeocodeListener listener) {
        executor.execute(() -> {
            try {
                // Basic fuzzy search logic. 
                // OpenAddresses usually has: NUMBER, STREET, CITY, POSTCODE
                // We will construct a query to search these fields.
                // Using DuckDB's jaro_winkler_similarity or simpler LIKE for now.
                
                String sql = String.format(
                    "SELECT *, jaro_winkler_similarity(street, ?) as score " +
                    "FROM %s " +
                    "WHERE (street ILIKE ? OR number ILIKE ?) " +
                    "ORDER BY score DESC LIMIT 20", 
                    tableName);
                
                // Preparing parameters manually for the ILIKE parts
                String likeQuery = "%" + query + "%";
                
                try (Connection conn = duckDBService.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    
                    stmt.setString(1, query); // For score
                    stmt.setString(2, likeQuery); // For street ILIKE
                    stmt.setString(3, likeQuery); // For number ILIKE (maybe exact match is better for number)
                    
                    ResultSet rs = stmt.executeQuery();
                    List<Map<String, Object>> results = new ArrayList<>();
                    
                    int cols = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        for (int i = 1; i <= cols; i++) {
                            row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
                        }
                        results.add(row);
                    }
                    listener.onResults(results);
                }
            } catch (Exception e) {
                Log.e(TAG, "Geocode error", e);
                listener.onError(e.getMessage());
            }
        });
    }
}
