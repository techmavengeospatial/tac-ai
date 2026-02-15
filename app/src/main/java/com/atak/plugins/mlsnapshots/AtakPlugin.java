
package com.atak.plugins.mlsnapshots;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import com.atak.plugins.mlsnapshots.services.AIService;
import com.atak.plugins.mlsnapshots.services.MapLibreService;
import com.atak.plugins.mlsnapshots.services.GeoPackageService;
import com.atak.plugins.mlsnapshots.services.DuckDBService;
import com.atak.plugins.mlsnapshots.services.DataIngestionService;
import com.atak.plugins.mlsnapshots.servers.OgcApiServer;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;
import org.maplibre.gl.maps.MapLibreMap;
import transapps.maps.plugin.lifecycle.Lifecycle;
import gov.tak.api.plugin.AbstractPlugin;

import java.sql.SQLException;
import java.util.List;

public class AtakPlugin extends AbstractPlugin implements MapLibreMap.SnapshotReadyCallback {

    public final static String TAG = "AtakPlugin";

    private AIService aiService;
    private MapLibreService mapLibreService;
    private GeoPackageService geoPackageService;
    private DuckDBService duckDBService;
    private OgcApiServer ogcApiServer;
    private DataIngestionService dataIngestionService;
    private MapView mapView;

    public AtakPlugin(final Lifecycle lifecycle) {
        super(lifecycle);
    }

    @Override
    public void onStart(final Context context, final MapView view) {
        Log.d(TAG, "Starting plugin...");
        super.onStart(context, view);
        this.mapView = view;

        try {
            // Initialize services
            aiService = new AIService(context);
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

            // Take a snapshot after a delay to allow the map to render
            view.postDelayed(() -> {
                Log.d(TAG, "Requesting map snapshot...");
                mapLibreService.takeSnapshot(this);
            }, 5000); // 5-second delay

            Log.d(TAG, "Services initialized successfully.");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize services", e);
        }
    }

    @Override
    public void onStop(final Context context, final MapView view) {
        Log.d(TAG, "Stopping plugin...");
        if (aiService != null) {
            aiService.close();
        }
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

    @Override
    public void onSnapshotReady(@NonNull Bitmap snapshot) {
        Log.d(TAG, "Snapshot is ready.");
        if (snapshot != null) {
            // The new on-device AI service is text-only. The snapshot is not used.
            // This is an example of how to use the new asynchronous text generation.
            String prompt = "Give me a summary of the current tactical situation based on available data.";
            Log.d(TAG, "Sending prompt to AI for analysis: " + prompt);

            aiService.generateContent(prompt, new AIService.ResponseListener() {
                @Override
                public void onResponse(String response) {
                    mapView.post(() -> {
                        Log.d(TAG, "AI Analysis Result: " + response);
                        // Here you would typically display the result in a UI element
                    });
                }

                @Override
                public void onError(String error) {
                    mapView.post(() -> {
                        Log.e(TAG, "AI Error: " + error);
                    });
                }
            });

            // Recycle the bitmap to free up memory since it is not used by the AI service
            snapshot.recycle();
        } else {
            Log.e(TAG, "Snapshot was null.");
        }
    }
}
