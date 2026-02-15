package com.atak.plugins.mlsnapshots;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.map.ui.DropDownReceiver;
import com.atak.plugins.mlsnapshots.services.PlacesDataService;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.log.Log;

public class PlacesDownloadWidget extends DropDownReceiver {

    public static final String SHOW_WIDGET = "com.atak.plugins.mlsnapshots.PLACES_DOWNLOAD_WIDGET";
    private static final String TAG = "PlacesDownloadWidget";
    private final Context context;
    private final PlacesDataService placesDataService;
    private final MapView mapView;
    private View widgetView;
    private TextView statusText;

    public PlacesDownloadWidget(MapView mapView, Context context, PlacesDataService placesDataService) {
        super(mapView);
        this.context = context;
        this.mapView = mapView;
        this.placesDataService = placesDataService;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (SHOW_WIDGET.equals(intent.getAction())) {
            if (widgetView == null) {
                initializeView();
            }
            showDropDown(widgetView, HALF_WIDTH, HALF_HEIGHT, FULL_WIDTH, HALF_HEIGHT, false);
        }
    }

    private void initializeView() {
        LayoutInflater inflater = LayoutInflater.from(context);
        widgetView = inflater.inflate(R.layout.places_download_widget, null);
        statusText = widgetView.findViewById(R.id.download_status);
        Button downloadButton = widgetView.findViewById(R.id.download_places_button);

        downloadButton.setOnClickListener(v -> {
            GeoPoint center = mapView.getCenterPoint();
            // Simple logic: download around the center point for demonstration
            // In a real app, use mapView.getBounds() to get the actual view extent
            // Assuming a small fixed radius for safety to avoid massive downloads
            double lat = center.getLatitude();
            double lon = center.getLongitude();
            double delta = 0.05; // Approx 5km radius
            
            statusText.setText("Starting Download...");
            placesDataService.downloadAndFusePlaces(lon - delta, lat - delta, lon + delta, lat + delta);
            Toast.makeText(context, "Places download started in background.", Toast.LENGTH_SHORT).show();
            // Ideally update status based on callbacks from service, but this is async
        });
    }

    @Override
    public void onDropDownSelectionRemoved() {}

    @Override
    public void onDropDownVisible(boolean v) {}

    @Override
    public void onDropDownSizeChanged(double width, double height) {}

    @Override
    public void onDropDownClose() {}
}
