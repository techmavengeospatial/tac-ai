
package com.atak.plugins.mlsnapshots;

import com.atak.plugins.mlsnapshots.services.AIService;
import com.atak.plugins.mlsnapshots.services.MapLibreService;
import com.atak.plugins.mlsnapshots.services.GeoPackageService;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;
import transapps.maps.plugin.lifecycle.Lifecycle;
import gov.tak.api.plugin.AbstractPlugin;
import android.content.Context;

public class AtakPlugin extends AbstractPlugin {

    public final static String TAG = "AtakPlugin";

    private AIService aiService;
    private MapLibreService mapLibreService;
    private GeoPackageService geoPackageService;

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

            // Example of how to use the GeoPackageService
            // String geoPackagePath = "/sdcard/data/basemap.gpkg";
            // if (geoPackageService.openGeoPackage(geoPackagePath)) {
            //     Log.d(TAG, "GeoPackage opened successfully");
            //     List<String> vectorTileTables = geoPackageService.getVectorTileTables();
            //     Log.d(TAG, "Vector Tile Tables: " + vectorTileTables);
            // } else {
            //     Log.e(TAG, "Failed to open GeoPackage");
            // }

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
        super.onStop(context, view);
        Log.d(TAG, "Plugin stopped.");
    }
}
