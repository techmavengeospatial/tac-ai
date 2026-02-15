
package com.atak.plugins.mlsnapshots;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.atak.plugins.mlsnapshots.services.PmTilesService;
import com.atak.plugins.mlsnapshots.servers.PmTilesServer;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atak.coremap.filesystem.FileSystemUtils;
import com.atak.coremap.log.Log;
import java.io.File;

public class PmTilesWidget extends DropDownReceiver implements OnStateListener, View.OnClickListener {

    public static final String TAG = "PmTilesWidget";
    public static final String SHOW_WIDGET = "com.atak.plugins.mlsnapshots.SHOW_PMTILES_WIDGET";

    private final Context pluginContext;
    private final View widgetView;
    private final PmTilesService pmTilesService;
    private final PmTilesServer pmTilesServer;
    private TextView statusText;
    private Button selectButton;
    private Button startButton;

    public PmTilesWidget(final MapView mapView, final Context context) {
        super(mapView);
        this.pluginContext = context;
        this.pmTilesService = new PmTilesService();
        this.pmTilesServer = new PmTilesServer(8081, pmTilesService);

        LayoutInflater inflater = LayoutInflater.from(pluginContext);
        widgetView = inflater.inflate(R.layout.pmtiles_widget, null);

        statusText = widgetView.findViewById(R.id.pmtiles_status_text);
        selectButton = widgetView.findViewById(R.id.select_pmtiles_button);
        startButton = widgetView.findViewById(R.id.start_pmtiles_server_button);
        
        selectButton.setOnClickListener(this);
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
        if (v.getId() == R.id.select_pmtiles_button) {
            // For this example, we'll try to use a sample file from the standard ATAK location
            File sampleFile = new File(FileSystemUtils.getItem("atak/tools/sample.pmtiles").getPath());
            if (sampleFile.exists()) {
                pmTilesService.setFile(sampleFile);
                statusText.setText("Selected: " + sampleFile.getName());
                startButton.setEnabled(true);
            } else {
                statusText.setText("Status: Sample file not found.");
            }
        } else if (v.getId() == R.id.start_pmtiles_server_button) {
            pmTilesServer.start();
            statusText.setText("Status: Server started at " + pmTilesServer.getTileUrl());
        }
    }
}
