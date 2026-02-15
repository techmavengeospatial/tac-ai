
package com.example.atak;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;
import transapps.maps.plugin.lifecycle.Lifecycle;
import gov.tak.api.plugin.AbstractPlugin;
import android.content.Context;

import com.example.atak.services.AIService;
import com.example.atak.services.WebServerService;

public class AtakPlugin extends AbstractPlugin {

    public final static String TAG = "AtakPlugin";

    private AIService aiService;

    public AtakPlugin(final Lifecycle lifecycle) {
        super(lifecycle);
    }

    @Override
    public void onStart(final Context context, final MapView view) {
        Log.d(TAG, "Starting plugin...");
        super.onStart(context, view);

        try {
            // Initialize services
            aiService = new AIService();

            // Start the web server in a new thread to avoid blocking the UI
            new Thread(() -> {
                WebServerService.main(new String[]{});
            }).start();

            Log.d(TAG, "Services initialized successfully.");

            // Test the AI service
            String aiResponse = aiService.sendMessage("Hello");
            Log.d(TAG, "AI Service Response: " + aiResponse);

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize services", e);
        }
    }

    @Override
    public void onStop(final Context context, final MapView view) {
        Log.d(TAG, "Stopping plugin...");
        super.onStop(context, view);
        // The web server and its services are shut down by the Spring context
        Log.d(TAG, "Plugin stopped.");
    }
}
