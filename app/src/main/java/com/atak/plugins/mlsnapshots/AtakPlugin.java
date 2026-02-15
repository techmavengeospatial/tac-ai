
package com.atak.plugins.mlsnapshots;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.NonNull;

import com.atak.plugins.mlsnapshots.services.AIService;
import com.atak.plugins.mlsnapshots.services.MapLibreService;
import com.atak.plugins.mlsnapshots.services.GeoPackageService;
import com.atak.plugins.mlsnapshots.services.DuckDBService;
import com.atak.plugins.mlsnapshots.services.DataIngestionService;
import com.atak.plugins.mlsnapshots.servers.OgcApiServer;
import com.atak.plugins.mlsnapshots.Google3DTilesWidget;
import com.atak.plugins.mlsnapshots.ModelConversionWidget;
import com.atak.plugins.mlsnapshots.PmTilesWidget;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventListener;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManager;
import com.atakmap.coremap.log.Log;
import org.maplibre.gl.maps.MapLibreMap;
import transapps.maps.plugin.lifecycle.Lifecycle;
import gov.tak.api.plugin.AbstractPlugin;

import java.sql.SQLException;

public class AtakPlugin extends AbstractPlugin implements MapLibreMap.SnapshotReadyCallback, MapEventListener {

    public final static String TAG = "AtakPlugin";

    private AIService aiService;
    private MapLibreService mapLibreService;
    private GeoPackageService geoPackageService;
    private DuckDBService duckDBService;
    private OgcApiServer ogcApiServer;
    private DataIngestionService dataIngestionService;
    private MapView mapView;
    private StylingWidgetDropDownReceiver stylingWidgetDropDownReceiver;
    private Google3DTilesWidget google3DTilesWidget;
    private ModelConversionWidget modelConversionWidget;
    private PmTilesWidget pmTilesWidget;

    public AtakPlugin(final Lifecycle lifecycle) {
        super(lifecycle);
    }

    @Override
    public void onStart(final Context context, final MapView view) {
        Log.d(TAG, "Starting plugin...");
        super.onStart(context, view);
        this.mapView = view;

        try {
            aiService = new AIService(context);
            mapLibreService = new MapLibreService(view);
            geoPackageService = new GeoPackageService(context, "atak_data.gpkg");
            dataIngestionService = new DataIngestionService(context, geoPackageService);
            duckDBService = new DuckDBService(geoPackageService.getGeoPackagePath());
            ogcApiServer = new OgcApiServer(8080, duckDBService, geoPackageService);
            stylingWidgetDropDownReceiver = new StylingWidgetDropDownReceiver(view, context, geoPackageService, mapLibreService);
            google3DTilesWidget = new Google3DTilesWidget(view, context, geoPackageService);
            modelConversionWidget = new ModelConversionWidget(view, context);
            pmTilesWidget = new PmTilesWidget(view, context);

            dataIngestionService.start();
            ogcApiServer.start();

            mapView.addMapEventListener(this);

            View stylingToolbar = View.inflate(context, R.layout.styling_widget_toolbar, null);
            stylingToolbar.findViewById(R.id.styling_widget_button).setOnClickListener(v -> {
                Intent intent = new Intent(StylingWidgetDropDownReceiver.SHOW_STYLING_WIDGET);
                context.sendBroadcast(intent);
            });

            Tool stylingWidgetTool = new Tool.Builder().setName("Styling").setIcon(R.drawable.ic_layers).setWidget(stylingToolbar).build();
            ToolManager.getInstance().addTool(stylingWidgetTool);
            
            View google3dToolbar = View.inflate(context, R.layout.google_3d_tiles_toolbar, null);
            google3dToolbar.findViewById(R.id.google_3d_tiles_widget_button).setOnClickListener(v -> {
                Intent intent = new Intent(Google3DTilesWidget.SHOW_WIDGET);
                context.sendBroadcast(intent);
            });

            Tool google3dTool = new Tool.Builder().setName("Google 3D").setIcon(R.drawable.ic_3d_rotation).setWidget(google3dToolbar).build();
            ToolManager.getInstance().addTool(google3dTool);
            
            View modelConversionToolbar = View.inflate(context, R.layout.model_conversion_toolbar, null);
            modelConversionToolbar.findViewById(R.id.model_conversion_widget_button).setOnClickListener(v -> {
                Intent intent = new Intent(ModelConversionWidget.SHOW_WIDGET);
                context.sendBroadcast(intent);
            });

            Tool modelConversionTool = new Tool.Builder().setName("Convert 3D").setIcon(R.drawable.ic_menu_3d).setWidget(modelConversionToolbar).build();
            ToolManager.getInstance().addTool(modelConversionTool);
            
            View pmTilesToolbar = View.inflate(context, R.layout.pmtiles_toolbar, null);
            pmTilesToolbar.findViewById(R.id.pmtiles_widget_button).setOnClickListener(v -> {
                Intent intent = new Intent(PmTilesWidget.SHOW_WIDGET);
                context.sendBroadcast(intent);
            });

            Tool pmTilesTool = new Tool.Builder().setName("PMTiles").setIcon(R.drawable.ic_map).setWidget(pmTilesToolbar).build();
            ToolManager.getInstance().addTool(pmTilesTool);

            Log.d(TAG, "All services initialized and started successfully.");
            Log.d(TAG, "Place vector files (Shapefile, KML, etc.) in 'atak/files/imports' to begin.");

            view.postDelayed(() -> {
                Log.d(TAG, "Requesting map snapshot...");
                mapLibreService.takeSnapshot(this);
            }, 5000);

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
        if (dataIngestionService != null) {
            dataIngestionService.stop();
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
        if (stylingWidgetDropDownReceiver != null) {
            stylingWidgetDropDownReceiver.dispose();
        }
        if (google3DTilesWidget != null) {
            google3DTilesWidget.dispose();
        }
        if(modelConversionWidget != null) {
            modelConversionWidget.dispose();
        }
        if(pmTilesWidget != null) {
            pmTilesWidget.dispose();
        }
        mapView.removeMapEventListener(this);
        super.onStop(context, view);
        Log.d(TAG, "Plugin stopped.");
    }

    @Override
    public void onSnapshotReady(Bitmap snapshot) {
        Log.d(TAG, "Snapshot is ready.");
        if (snapshot != null) {
            String prompt = "Give me a summary of the current tactical situation based on available data.";
            Log.d(TAG, "Sending prompt to AI for analysis: " + prompt);

            aiService.generateContent(prompt, new AIService.ResponseListener() {
                @Override
                public void onResponse(String response) {
                    mapView.post(() -> Log.d(TAG, "AI Analysis Result: " + response));
                }

                @Override
                public void onError(String error) {
                    mapView.post(() -> Log.e(TAG, "AI Error: " + error));
                }
            });
            snapshot.recycle();
        } else {
            Log.e(TAG, "Snapshot was null.");
        }
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (event.getType().equals(MapEvent.MAP_ROTATED)) {
            double bearing = mapView.getMapRotation();
            mapLibreService.updateRasterTileSources(bearing);
        }
    }
}
