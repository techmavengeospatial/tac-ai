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
import com.atak.plugins.mlsnapshots.services.EsriDataService;
import com.atak.plugins.mlsnapshots.services.PlacesDataService;
import com.atak.plugins.mlsnapshots.servers.OgcApiServer;
import com.atak.plugins.mlsnapshots.Google3DTilesWidget;
import com.atak.plugins.mlsnapshots.ModelConversionWidget;
import com.atak.plugins.mlsnapshots.PmTilesWidget;
import com.atak.plugins.mlsnapshots.ModelDownloadWidget;
import com.atak.plugins.mlsnapshots.PlacesDownloadWidget;

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
    private EsriDataService esriDataService;
    private PlacesDataService placesDataService;
    private MapView mapView;
    private StylingWidgetDropDownReceiver stylingWidgetDropDownReceiver;
    private Google3DTilesWidget google3DTilesWidget;
    private ModelConversionWidget modelConversionWidget;
    private PmTilesWidget pmTilesWidget;
    private ModelDownloadWidget modelDownloadWidget;
    private PlacesDownloadWidget placesDownloadWidget;

    public AtakPlugin(final Lifecycle lifecycle) {
        super(lifecycle);
    }

    @Override
    public void onStart(final Context context, final MapView view) {
        Log.d(TAG, "Starting plugin...");
        super.onStart(context, view);
        this.mapView = view;

        try {
            aiService = new AIService(context, AIService.ModelType.GEMMA_3N);
            mapLibreService = new MapLibreService(view);
            geoPackageService = new GeoPackageService(context, "atak_data.gpkg");
            dataIngestionService = new DataIngestionService(context, geoPackageService);
            duckDBService = new DuckDBService(geoPackageService.getGeoPackagePath());
            esriDataService = new EsriDataService(duckDBService);
            placesDataService = new PlacesDataService(duckDBService);
            ogcApiServer = new OgcApiServer(8080, duckDBService, geoPackageService);
            
            stylingWidgetDropDownReceiver = new StylingWidgetDropDownReceiver(view, context, geoPackageService, mapLibreService);
            google3DTilesWidget = new Google3DTilesWidget(view, context, geoPackageService);
            modelConversionWidget = new ModelConversionWidget(view, context);
            pmTilesWidget = new PmTilesWidget(view, context);
            modelDownloadWidget = new ModelDownloadWidget(view, context);
            placesDownloadWidget = new PlacesDownloadWidget(view, context, placesDataService);

            dataIngestionService.start();
            ogcApiServer.start();

            mapView.addMapEventListener(this);
            
            // Add a sample ESRI Feature Server Layer
            String layerName = "Raleigh_Vehicles";
            String serviceUrl = "https://maps.raleighnc.gov/arcgis/rest/services/PublicUtility/VehicleLocator/MapServer";
            String layerId = "0";
            String refreshCron = "*/30 * * * * *"; // Every 30 seconds
            esriDataService.addFeatureServerLayer(layerName, serviceUrl, layerId, refreshCron);

            // Create toolbars
            createToolbar(context, StylingWidgetDropDownReceiver.SHOW_STYLING_WIDGET, "Styling", R.drawable.ic_layers);
            createToolbar(context, Google3DTilesWidget.SHOW_WIDGET, "Google 3D", R.drawable.ic_3d_rotation);
            createToolbar(context, ModelConversionWidget.SHOW_WIDGET, "Convert 3D", R.drawable.ic_menu_3d);
            createToolbar(context, PmTilesWidget.SHOW_WIDGET, "PMTiles", R.drawable.ic_map);
            createToolbar(context, ModelDownloadWidget.SHOW_WIDGET, "AI Models", R.drawable.ic_download);
            createToolbar(context, PlacesDownloadWidget.SHOW_WIDGET, "Places", R.drawable.ic_place);

            Log.d(TAG, "All services initialized and started successfully.");
            view.postDelayed(() -> {
                mapLibreService.takeSnapshot(this);
            }, 5000);

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize services", e);
        }
    }
    
    private void createToolbar(Context context, String action, String name, int iconResId) {
        View toolbarView = View.inflate(context, R.layout.generic_toolbar, null);
        ImageButton button = toolbarView.findViewById(R.id.toolbar_button);
        button.setImageResource(iconResId);
        button.setOnClickListener(v -> {
            Intent intent = new Intent(action);
            context.sendBroadcast(intent);
        });

        Tool tool = new Tool.Builder().setName(name).setIcon(iconResId).setWidget(toolbarView).build();
        ToolManager.getInstance().addTool(tool);
    }

    @Override
    public void onStop(final Context context, final MapView view) {
        Log.d(TAG, "Stopping plugin...");
        if (aiService != null) aiService.close();
        if (dataIngestionService != null) dataIngestionService.stop();
        if (ogcApiServer != null) ogcApiServer.stop();
        if (duckDBService != null) {
            try {
                duckDBService.close();
            } catch (SQLException e) {
                Log.e(TAG, "Failed to close DuckDBService", e);
            }
        }
        if (stylingWidgetDropDownReceiver != null) stylingWidgetDropDownReceiver.dispose();
        if (google3DTilesWidget != null) google3DTilesWidget.dispose();
        if(modelConversionWidget != null) modelConversionWidget.dispose();
        if(pmTilesWidget != null) pmTilesWidget.dispose();
        if(modelDownloadWidget != null) modelDownloadWidget.dispose();
        if(placesDownloadWidget != null) placesDownloadWidget.dispose();
        
        mapView.removeMapEventListener(this);
        super.onStop(context, view);
        Log.d(TAG, "Plugin stopped.");
    }

    @Override
    public void onSnapshotReady(Bitmap snapshot) {
        if (snapshot != null) snapshot.recycle();
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (event.getType().equals(MapEvent.MAP_ROTATED)) {
            double bearing = mapView.getMapRotation();
            mapLibreService.updateRasterTileSources(bearing);
        }
    }
}
