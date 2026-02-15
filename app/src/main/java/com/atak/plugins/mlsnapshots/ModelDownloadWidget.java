package com.atak.plugins.mlsnapshots;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.map.ui.DropDownReceiver;
import com.atak.plugins.mlsnapshots.services.ModelDownloadService;

import java.io.File;

public class ModelDownloadWidget extends DropDownReceiver {

    public static final String SHOW_WIDGET = "com.atak.plugins.mlsnapshots.MODEL_DOWNLOAD_WIDGET";
    private final Context context;
    private final ModelDownloadService downloadService;
    private View widgetView;
    private LinearLayout modelListLayout;

    public ModelDownloadWidget(com.atakmap.android.maps.MapView mapView, Context context) {
        super(mapView);
        this.context = context;
        this.downloadService = new ModelDownloadService();
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
        widgetView = inflater.inflate(R.layout.model_download_widget, null);
        modelListLayout = widgetView.findViewById(R.id.model_list_layout);

        addModelItem("Gemma 2B", "https://example.com/gemma-2b-it-cpu-int4.tflite", "gemma-2b-it-cpu-int4.tflite");
        addModelItem("Gemma 3N", "https://example.com/gemma-3n-it-cpu-int4.tflite", "gemma-3n-it-cpu-int4.tflite");
        addModelItem("Phi-4", "https://example.com/phi-4-cpu-int4.tflite", "phi-4-cpu-int4.tflite");
    }

    private void addModelItem(String modelName, String downloadUrl, String fileName) {
        View itemView = LayoutInflater.from(context).inflate(R.layout.model_download_item, null);
        TextView nameText = itemView.findViewById(R.id.model_name);
        Button downloadButton = itemView.findViewById(R.id.download_button);
        ProgressBar progressBar = itemView.findViewById(R.id.download_progress);
        TextView statusText = itemView.findViewById(R.id.download_status);

        nameText.setText(modelName);
        
        File modelFile = new File(context.getExternalFilesDir(null), fileName);
        if (modelFile.exists()) {
            statusText.setText("Downloaded");
            statusText.setTextColor(Color.GREEN);
            downloadButton.setVisibility(View.GONE);
        } else {
            statusText.setText("Not Downloaded");
            statusText.setTextColor(Color.RED);
        }

        downloadButton.setOnClickListener(v -> {
            downloadButton.setEnabled(false);
            statusText.setText("Downloading...");
            statusText.setTextColor(Color.BLUE);
            progressBar.setVisibility(View.VISIBLE);
            
            downloadService.downloadModel(downloadUrl, modelFile, new ModelDownloadService.DownloadListener() {
                @Override
                public void onProgress(int progress) {
                    progressBar.setProgress(progress);
                }

                @Override
                public void onComplete(File file) {
                    widgetView.post(() -> {
                        statusText.setText("Downloaded");
                        statusText.setTextColor(Color.GREEN);
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(context, modelName + " downloaded successfully!", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String error) {
                    widgetView.post(() -> {
                        statusText.setText("Error");
                        statusText.setTextColor(Color.RED);
                        progressBar.setVisibility(View.GONE);
                        downloadButton.setEnabled(true);
                        Toast.makeText(context, "Download failed: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        modelListLayout.addView(itemView);
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
