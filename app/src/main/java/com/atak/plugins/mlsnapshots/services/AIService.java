
package com.atak.plugins.mlsnapshots.services;

import android.content.Context;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions;
import com.atak.coremap.log.Log;

public class AIService {

    public final static String TAG = "AIService";

    // FIXME: Add your TFLite model to the assets folder and update this path.
    // Models like Gemma 2B are a good starting point.
    private static final String MODEL_PATH = "gemma-2b-it-cpu-int4.tflite";

    private LlmInference llmInference;

    // Define a callback interface for handling the response
    public interface ResponseListener {
        void onResponse(String response);
        void onError(String error);
    }

    public AIService(Context context) {
        try {
            // It is recommended to create a single LlmInference instance for the entire app.
            // The LlmInference is thread-safe.
            LlmInferenceOptions options = LlmInferenceOptions.builder()
                .setModelPath(MODEL_PATH)
                .build();
            llmInference = LlmInference.createFromOptions(context, options);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing LlmInference", e);
        }
    }

    /**
     * Generates content asynchronously. The result is returned through the listener.
     *
     * @param prompt The input prompt for the model.
     * @param listener The listener to handle the response or error.
     */
    public void generateContent(String prompt, ResponseListener listener) {
        if (llmInference == null) {
            listener.onError("LlmInference is not initialized.");
            return;
        }

        // The generateResponseAsync method is non-blocking and will return the result
        // in the provided callback.
        llmInference.generateResponseAsync(prompt, result -> {
            if (result != null) {
                listener.onResponse(result);
            } else {
                listener.onError("Failed to generate response.");
            }
        });
    }

    /**
     * Releases the resources used by the LlmInference model.
     * Call this when the service is being destroyed.
     */
    public void close() {
        if (llmInference != null) {
            llmInference.close();
        }
    }
}
