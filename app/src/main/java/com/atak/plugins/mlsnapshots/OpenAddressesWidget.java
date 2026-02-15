
package com.atak.plugins.mlsnapshots;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import com.atak.plugins.mlsnapshots.services.DuckDBService;
import com.atak.plugins.mlsnapshots.services.OpenAddressesService;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atak.coremap.log.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OpenAddressesWidget extends DropDownReceiver implements OnStateListener, View.OnClickListener {

    public static final String TAG = "OpenAddressesWidget";
    public static final String SHOW_WIDGET = "com.atak.plugins.mlsnapshots.SHOW_OPENADDRESSES_WIDGET";

    private final Context pluginContext;
    private final View widgetView;
    private final OpenAddressesService oaService;
    private EditText downloadUrlInput;
    private EditText searchInput;
    private TextView statusText;
    private ListView resultsList;
    private ArrayAdapter<String> adapter;

    public OpenAddressesWidget(final MapView mapView, final Context context, DuckDBService duckDBService) {
        super(mapView);
        this.pluginContext = context;
        File dataDir = new File(context.getExternalFilesDir(null), "openaddresses");
        this.oaService = new OpenAddressesService(duckDBService, dataDir);

        LayoutInflater inflater = LayoutInflater.from(pluginContext);
        widgetView = inflater.inflate(R.layout.openaddresses_widget, null);

        downloadUrlInput = widgetView.findViewById(R.id.oa_download_url);
        searchInput = widgetView.findViewById(R.id.oa_search_input);
        statusText = widgetView.findViewById(R.id.oa_status_text);
        resultsList = widgetView.findViewById(R.id.oa_results_list);
        
        Button downloadButton = widgetView.findViewById(R.id.oa_download_button);
        Button searchButton = widgetView.findViewById(R.id.oa_search_button);
        
        downloadButton.setOnClickListener(this);
        searchButton.setOnClickListener(this);
        
        adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, new ArrayList<>());
        resultsList.setAdapter(adapter);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(SHOW_WIDGET)) {
            if (!isVisible()) {
                showDropDown(widgetView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT, this);
            }
        }
    }

    @Override
    public void onDropDownStateChanged(int state) {}
    @Override
    public void onDropDownSizeChanged(double width, double height) {}
    @Override
    public void onDropDownClose() {}

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.oa_download_button) {
            String url = downloadUrlInput.getText().toString();
            if (url.isEmpty()) {
                statusText.setText("Please enter a URL.");
                return;
            }
            statusText.setText("Starting download...");
            
            // Assume we save to a table named 'oa_addresses' for now
            oaService.downloadAndIngest(url, "oa_addresses", new OpenAddressesService.ResultListener() {
                @Override
                public void onSuccess(String message) {
                    getMapView().post(() -> statusText.setText(message));
                }
                @Override
                public void onError(String error) {
                    getMapView().post(() -> statusText.setText("Error: " + error));
                }
            });
            
        } else if (v.getId() == R.id.oa_search_button) {
            String query = searchInput.getText().toString();
            if (query.isEmpty()) return;
            
            oaService.geocode("oa_addresses", query, new OpenAddressesService.GeocodeListener() {
                @Override
                public void onResults(List<Map<String, Object>> results) {
                    getMapView().post(() -> {
                        adapter.clear();
                        if (results.isEmpty()) {
                            adapter.add("No results found.");
                        } else {
                            for (Map<String, Object> row : results) {
                                // Formatting result for display: Number Street, City, Postcode
                                String text = String.format("%s %s, %s %s", 
                                    row.getOrDefault("NUMBER", ""),
                                    row.getOrDefault("STREET", ""),
                                    row.getOrDefault("CITY", ""),
                                    row.getOrDefault("POSTCODE", ""));
                                adapter.add(text);
                            }
                        }
                    });
                }
                @Override
                public void onError(String error) {
                     getMapView().post(() -> statusText.setText("Search Error: " + error));
                }
            });
        }
    }
}
