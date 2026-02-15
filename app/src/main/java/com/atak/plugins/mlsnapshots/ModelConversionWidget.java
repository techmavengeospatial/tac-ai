
package com.atak.plugins.mlsnapshots;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.atak.plugins.mlsnapshots.services.ModelConversionService;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atak.coremap.filesystem.FileSystemUtils;
import com.atak.coremap.log.Log;
import java.io.File;

public class ModelConversionWidget extends DropDownReceiver implements OnStateListener, View.OnClickListener {

    public static final String TAG = "ModelConversionWidget";
    public static final String SHOW_WIDGET = "com.atak.plugins.mlsnapshots.SHOW_MODEL_CONVERSION_WIDGET";

    private final Context pluginContext;
    private final View widgetView;
    private final ModelConversionService conversionService;
    private TextView statusText;

    public ModelConversionWidget(final MapView mapView, final Context context) {
        super(mapView);
        this.pluginContext = context;
        this.conversionService = new ModelConversionService();
        
        LayoutInflater inflater = LayoutInflater.from(pluginContext);
        widgetView = inflater.inflate(R.layout.model_conversion_widget, null);

        statusText = widgetView.findViewById(R.id.conversion_status_text);
        Button startButton = widgetView.findViewById(R.id.start_conversion_button);
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
        if (v.getId() == R.id.start_conversion_button) {
            statusText.setText("Status: Starting conversion...");
            // For this example, we'll imagine a sample file is present in the file system.
            File sampleGltf = new File(FileSystemUtils.getItem("atak/tools/sample.gltf").getPath());
            File outputDir = FileSystemUtils.getItem("atak/tools/models_output");
            outputDir.mkdirs();

            conversionService.convertGltfToObj(sampleGltf, outputDir, new ModelConversionService.ConversionListener() {
                @Override
                public void onProgress(String message) {
                    getMapView().post(() -> statusText.setText("Status: " + message));
                }

                @Override
                public void onComplete(boolean success, String outputPath) {
                    getMapView().post(() -> statusText.setText("Status: Conversion " + (success ? "complete. Output: " + outputPath : "failed.")));
                }
            });
        }
    }
}
