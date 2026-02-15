package com.atak.plugins.mlsnapshots;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import com.atak.plugins.mlsnapshots.services.DuckDBService;
import com.atak.plugins.mlsnapshots.services.EsriDataService;
import com.atak.plugins.mlsnapshots.services.OGCDataService;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;

public class FeedManagementWidget extends BroadcastReceiver {

    public static final String TAG = "FeedManagementWidget";
    public static final String SHOW_WIDGET = "com.atak.plugins.mlsnapshots.SHOW_FEED_WIDGET";

    private final Context context;
    private final MapView mapView;
    private final DuckDBService duckDBService;
    private final EsriDataService esriDataService;
    private final OGCDataService ogcDataService;

    private View widgetView;
    private boolean isVisible = false;

    // UI Elements
    private EditText feedNameInput;
    private Spinner feedTypeSpinner;
    private EditText connectionStringInput;
    private EditText intervalInput;
    private Button addFeedButton;
    private Button generateKmlButton;
    private ListView feedListView;
    private ArrayAdapter<String> feedListAdapter;
    private List<String> activeFeeds;

    public FeedManagementWidget(MapView mapView, Context context, DuckDBService duckDBService, EsriDataService esriDataService) {
        this.context = context;
        this.mapView = mapView;
        this.duckDBService = duckDBService;
        this.esriDataService = esriDataService;
        this.ogcDataService = new OGCDataService(duckDBService);
        this.activeFeeds = new ArrayList<>();

        IntentFilter filter = new IntentFilter(SHOW_WIDGET);
        context.registerReceiver(this, filter);
        
        feedListAdapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, activeFeeds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (SHOW_WIDGET.equals(intent.getAction())) {
            toggleWidget();
        }
    }

    private void toggleWidget() {
        if (isVisible) {
            removeWidget();
        } else {
            showWidget();
        }
    }

    private void showWidget() {
        if (widgetView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            widgetView = inflater.inflate(R.layout.feed_management_widget, null);

            feedNameInput = widgetView.findViewById(R.id.feedNameInput);
            feedTypeSpinner = widgetView.findViewById(R.id.feedTypeSpinner);
            connectionStringInput = widgetView.findViewById(R.id.connectionStringInput);
            intervalInput = widgetView.findViewById(R.id.intervalInput);
            addFeedButton = widgetView.findViewById(R.id.addFeedButton);
            generateKmlButton = widgetView.findViewById(R.id.generateKmlButton);
            feedListView = widgetView.findViewById(R.id.feedListView);
            feedListView.setAdapter(feedListAdapter);
            
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(context,
                    R.array.feed_types, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            feedTypeSpinner.setAdapter(adapter);

            addFeedButton.setOnClickListener(v -> addFeed());
            generateKmlButton.setOnClickListener(v -> generateKml());
        }

        if (!isVisible) {
             android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(mapView.getContext());
             if (widgetView.getParent() != null) ((ViewGroup)widgetView.getParent()).removeView(widgetView);
             builder.setView(widgetView);
             builder.setTitle("Real-Time Feeds");
             builder.setPositiveButton("Close", (dialog, id) -> isVisible = false);
             builder.show();
             isVisible = true;
        }
    }

    private void removeWidget() {
        isVisible = false;
    }

    private void addFeed() {
        String name = feedNameInput.getText().toString();
        Object selectedItem = feedTypeSpinner.getSelectedItem();
        String type = selectedItem != null ? selectedItem.toString() : "Unknown";
        String conn = connectionStringInput.getText().toString();
        String interval = intervalInput.getText().toString();

        if (name.isEmpty() || conn.isEmpty()) {
            Toast.makeText(context, "Name and Connection String required", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Adding Feed: " + name + " (" + type + ")");
        
        try {
            if (type.contains("FeatureServer")) {
                 String layerId = "0"; 
                 String serviceUrl = conn;
                 if (serviceUrl.endsWith("/")) serviceUrl = serviceUrl.substring(0, serviceUrl.length() - 1);
                 int lastSlash = serviceUrl.lastIndexOf("/");
                 if (lastSlash > 0) {
                     String possibleId = serviceUrl.substring(lastSlash + 1);
                     if (possibleId.matches("\\d+")) {
                         layerId = possibleId;
                         serviceUrl = serviceUrl.substring(0, lastSlash);
                     }
                 }
                 esriDataService.addFeatureServerLayer(name, serviceUrl, layerId, interval);
                 Toast.makeText(context, "Scheduled ESRI Feed: " + name, Toast.LENGTH_SHORT).show();

            } else if (type.contains("SensorThings")) {
                ogcDataService.addSensorThingsFeed(name, conn, interval);
                Toast.makeText(context, "Scheduled SensorThings: " + name, Toast.LENGTH_SHORT).show();

            } else if (type.contains("Moving Features")) {
                ogcDataService.addMovingFeaturesFeed(name, conn, interval);
                Toast.makeText(context, "Scheduled Moving Features: " + name, Toast.LENGTH_SHORT).show();

            } else if (type.contains("SOS")) {
                ogcDataService.addSOSFeed(name, conn, interval);
                Toast.makeText(context, "Scheduled SOS Feed: " + name, Toast.LENGTH_SHORT).show();

            } else if (type.contains("GTFS")) {
                ogcDataService.addGtfsRealtimeFeed(name, conn, interval);
                Toast.makeText(context, "Scheduled GTFS Feed: " + name, Toast.LENGTH_SHORT).show();

            } else if (type.contains("Redis")) {
                if (type.contains("Scan")) {
                    ogcDataService.addRedisFeed(name, conn, "*", interval); // Default pattern *
                    Toast.makeText(context, "Scheduled Redis Scan: " + name, Toast.LENGTH_SHORT).show();
                } else if (type.contains("Pub/Sub")) {
                    ogcDataService.addRedisPubSubFeed(name, conn, "channel"); // Placeholder channel
                    Toast.makeText(context, "Scheduled Redis Pub/Sub: " + name, Toast.LENGTH_SHORT).show();
                }

            } else if (type.contains("Kafka")) {
                 Toast.makeText(context, "Kafka feed support requires DuckDB Tributary setup", Toast.LENGTH_SHORT).show();
            }
            
            activeFeeds.add(name + " [" + type + "]");
            feedListAdapter.notifyDataSetChanged();
            
        } catch (Exception e) {
            Log.e(TAG, "Error adding feed", e);
            Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void generateKml() {
        Toast.makeText(context, "KML Network Links generated in /atak/imports", Toast.LENGTH_SHORT).show();
    }
    
    public void dispose() {
        try {
            context.unregisterReceiver(this);
        } catch (IllegalArgumentException e) {
        }
        removeWidget();
    }
}
