
package com.atak.plugins.mlsnapshots;

import com.atak.plugins.mlsnapshots.services.AIService;
import com.atak.plugins.mlsnapshots.services.MapLibreService;
import com.atak.plugins.mlsnapshots.services.GeoPackageService;
import com.atak.plugins.mlsnapshots.services.DuckDBService;
import com.atak.plugins.mlsnapshots.services.DataIngestionService;
import com.atak.plugins.mlsnapshots.servers.OgcApiServer;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;
import transapps.maps.plugin.lifecycle.Lifecycle;
import gov.tak.api.plugin.AbstractPlugin;
import android.content.Context;
import java.sql.SQLException;
import java.util.List;

public class AtakPlugin extends AbstractPlugin {

    public final static String TAG = "AtakPlugin";

    private AIService aiService;
    private MapLibreService mapLibreService;
    private GeoPackageService geoPackageService;
    private DuckDBService duckDBService;
    private OgcApiServer ogcApiServer;
    private DataIngestionService dataIngestionService;

    public AtakPlugin(final Lifecycle lifecycle) {
        super(lifecycle);
    }

    @Override
    public void onStart(final Context context, final MapView view) {
        Log.d(TAG, "Starting plugin...");
        super.onStart(context, view);

        try {
            // Initialize services
            aiService = new AIService("your-google-cloud-project-id", "us-central1", "gemini-1.0-pro-vision-001");
            mapLibreService = new MapLibreService(view);
            geoPackageService = new GeoPackageService(context);

            // Initialize DuckDB and OGC API Server
            try {
                duckDBService = new DuckDBService(":memory:");
                ogcApiServer = new OgcApiServer(8080, duckDBService, geoPackageService);
                dataIngestionService = new DataIngestionService(duckDBService);
                dataIngestionService.start();
                Log.d(TAG, "DuckDB, OGC API Server, and Data Ingestion Service initialized.");
            } catch (SQLException e) {
                Log.e(TAG, "Failed to initialize services", e);
            }

            // Example of how to use the GeoPackageService
            String geoPackagePath = "/sdcard/data/features_and_tiles.gpkg"; // IMPORTANT: Replace with the actual path to your GeoPackage file
            if (geoPackageService.openGeoPackage(geoPackagePath)) {
                Log.d(TAG, "GeoPackage opened successfully");

                // Handle Raster Tiles
                List<String> tileTables = geoPackageService.getTileTables();
                Log.d(TAG, "Found Raster Tile Tables: " + tileTables);
                for (String table : tileTables) {
                    String sourceId = "gpkg-raster-" + table;
                    String tileUrl = "http://localhost:8080/geopackage/" + table + "/{z}/{x}/{y}";
                    mapLibreService.addRasterTileSource(sourceId, tileUrl);
                    Log.d(TAG, "Added raster tile source '" + sourceId + "' with URL: " + tileUrl);
                }

                // Handle Vector Features
                List<String> featureTables = geoPackageService.getFeatureTables();
                Log.d(TAG, "Found Vector Feature Tables: " + featureTables);
                for (String table : featureTables) {
                    String sourceId = "gpkg-features-" + table;
                    String layerId = "gpkg-layer-" + table;
                    String geoJsonUrl = "http://localhost:8080/geopackage/features/" + table;
                    mapLibreService.addVectorSource(sourceId, geoJsonUrl);
                    mapLibreService.addVectorLayer(layerId, sourceId);
                    Log.d(TAG, "Added vector feature layer '" + layerId + "' from source: " + sourceId);
                }

                // Handle Vector Tiles
                List<String> vectorTileTables = geoPackageService.getVectorTileTables();
                Log.d(TAG, "Found Vector Tile Tables: " + vectorTileTables);
                for (String table : vectorTileTables) {
                    String sourceId = "gpkg-vectortiles-" + table;
                    String tileUrl = "http://localhost:8080/geopackage/vectortiles/" + table + "/{z}/{x}/{y}";
                    // The source-layer is often the same as the table name
                    mapLibreService.addVectorTileSource(sourceId, tileUrl, table);
                    Log.d(TAG, "Added vector tile source '" + sourceId + "' with URL: " + tileUrl);
                }

            } else {
                Log.e(TAG, "Failed to open GeoPackage at: " + geoPackagePath + ". Make sure the file exists and the app has storage permissions.");
            }

            Log.d(TAG, "Services initialized successfully.");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize services", e);
        }
    }

    @Override
    public void onStop(final Context context, final MapView view) {
        Log.d(TAG, "Stopping plugin...");
        if (geoPackageService != null) {
            geoPackageService.close();
        }
        if (dataIngestionService != null) {
            try {
                dataIngestionService.stop();
            } catch (SQLException e) {
                Log.e(TAG, "Failed to stop DataIngestionService", e);
            }
        }
        if (ogcApiServer != null) {
            ogcApiServer.stop();
        }
        if (duckDBService != null) {
            try {
                duckDBService.close();
            } catch (SQLException e) {
                Log.e(TAG, "Failed to close DuckDBService", e);
            }
        }
        super.onStop(context, view);
        Log.d(TAG, "Plugin stopped.");
    }
}
