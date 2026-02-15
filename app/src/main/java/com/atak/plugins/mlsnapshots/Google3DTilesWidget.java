
package com.atak.plugins.mlsnapshots;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.atak.plugins.mlsnapshots.services.GeoPackageService;
import com.atak.plugins.mlsnapshots.services.Google3DTilesService;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atak.coremap.log.Log;

public class Google3DTilesWidget extends DropDownReceiver implements OnStateListener, View.OnClickListener {

    public static final String TAG = "Google3DTilesWidget";
    public static final String SHOW_WIDGET = "com.atak.plugins.mlsnapshots.SHOW_3D_TILES_WIDGET";

    private final Context pluginContext;
    private final View widgetView;
    private final Google3DTilesService tilesService;
    private EditText apiKeyInput;
    private TextView statusText;

    public Google3DTilesWidget(final MapView mapView, final Context context, GeoPackageService geoPackageService) {
        super(mapView);
        this.pluginContext = context;
        this.tilesService = new Google3DTilesService(geoPackageService);
        
        LayoutInflater inflater = LayoutInflater.from(pluginContext);
        widgetView = inflater.inflate(R.layout.google_3d_tiles_widget, null);

        apiKeyInput = widgetView.findViewById(R.id.google_api_key_input);
        statusText = widgetView.findViewById(R.id.import_status_text);
        Button startButton = widgetView.findViewById(R.id.start_import_button);
        startButton.setOnClickListener(this);
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
        if (v.getId() == R.id.start_import_button) {
            String apiKey = apiKeyInput.getText().toString();
            if (apiKey.isEmpty()) {
                statusText.setText("Status: Please enter an API key.");
                return;
            }
            
            tilesService.startImport(apiKey, new Google3DTilesService.ProgressListener() {
                @Override
                public void onProgress(String message) {
                    getMapView().post(() -> statusText.setText("Status: " + message));
                }

                @Override
                public void onComplete(boolean success) {
                    getMapView().post(() -> statusText.setText("Status: Import " + (success ? "complete." : "failed.")));
                }
            });
        }
    }
}
