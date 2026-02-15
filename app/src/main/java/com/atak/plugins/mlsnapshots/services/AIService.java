
package com.atak.plugins.mlsnapshots.services;

import android.content.Context;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceOptions;
import com.atakmap.coremap.log.Log;
import java.io.File;

public class AIService {

    public final static String TAG = "AIService";

    private LlmInference llmInference;
    private final Context context;

    public interface ResponseListener {
        void onResponse(String response);
        void onPartialResponse(String partialText);
        void onError(String error);
    }

    public AIService(Context context) {
        this.context = context;
    }

    /**
     * Initializes the LLM inference engine with a specific model file.
     * This supports any MediaPipe-compatible .bin or .tflite model.
     * 
     * @param modelPath The absolute path to the model file on the device.
     */
    public void initializeModel(String modelPath) {
        if (!new File(modelPath).exists()) {
            Log.e(TAG, "Model file not found at: " + modelPath);
            return;
        }

        try {
            Log.d(TAG, "Initializing AI Service with model: " + modelPath);

            LlmInferenceOptions options = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024) // Increased context window
                .setTopK(40)
                .setTemperature(0.8f) // Slightly creative
                .setRandomSeed(42)
                .build();

            llmInference = LlmInference.createFromOptions(context, options);
            Log.d(TAG, "Model initialized successfully.");

        } catch (Exception e) {
            Log.e(TAG, "Error initializing LlmInference with model " + modelPath, e);
        }
    }

    public void generateContent(String prompt, ResponseListener listener) {
        if (llmInference == null) {
            listener.onError("LlmInference is not initialized. Please call initializeModel() first.");
            return;
        }

        // Use a background thread for generation to avoid blocking the UI
        new Thread(() -> {
            try {
                // For streaming response (better UX)
                llmInference.generateResponseAsync(prompt, (partialResult, done) -> {
                    if (partialResult != null) {
                        listener.onPartialResponse(partialResult);
                    }
                    if (done) {
                        listener.onResponse(" [DONE]"); 
                    }
                });
            } catch (Exception e) {
                listener.onError("Error during generation: " + e.getMessage());
            }
        }).start();
    }

    public void close() {
        // LlmInference currently doesn't have a public close() method in some versions,
        // but if it becomes available or for other cleanup:
        llmInference = null;
    }
}
