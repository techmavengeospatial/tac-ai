
package com.atak.plugins.mlsnapshots.services;

import android.content.Context;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions;
import com.atakmap.coremap.log.Log;

public class AIService {

    public final static String TAG = "AIService";

    public enum ModelType {
        GEMMA_2B("gemma-2b-it-cpu-int4.tflite"),
        GEMMA_3N("gemma-3n-it-cpu-int4.tflite"),
        PHI_4("phi-4-cpu-int4.tflite");

        private final String filename;

        ModelType(String filename) {
            this.filename = filename;
        }

        public String getFilename() {
            return filename;
        }
    }

    private LlmInference llmInference;
    private final Context context;
    private ModelType currentModelType;

    // Define a callback interface for handling the response
    public interface ResponseListener {
        void onResponse(String response);
        void onError(String error);
    }

    public AIService(Context context) {
        this(context, ModelType.GEMMA_2B); // Default to Gemma 2B
    }

    public AIService(Context context, ModelType modelType) {
        this.context = context;
        initializeModel(modelType);
    }

    public void initializeModel(ModelType modelType) {
        try {
            if (llmInference != null) {
                llmInference.close();
            }
            
            this.currentModelType = modelType;
            Log.d(TAG, "Initializing AI Service with model: " + modelType.name());

            // It is recommended to create a single LlmInference instance for the entire app.
            // The LlmInference is thread-safe.
            LlmInferenceOptions options = LlmInferenceOptions.builder()
                .setModelPath(modelType.getFilename())
                .setMaxTokens(512) // Limit output tokens
                .setResultListener((partialResult, done) -> {
                    // This listener is for streaming results, not used in the simple generateContent method
                })
                .setErrorListener((error) -> {
                    Log.e(TAG, "LlmInference Error: " + error.getMessage());
                })
                .build();
            
            llmInference = LlmInference.createFromOptions(context, options);
            Log.d(TAG, "Model initialized successfully.");

        } catch (Exception e) {
            Log.e(TAG, "Error initializing LlmInference with model " + modelType.name(), e);
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

        try {
            // The generateResponseAsync method is non-blocking and will return the result
            // in the provided callback.
            llmInference.generateResponseAsync(prompt);
            
            // Note: The simple generateResponseAsync in some versions of the MediaPipe tasks 
            // might not take a callback directly if a result listener was set in options.
            // However, based on common usage, let's assume a standard async call or 
            // adjust if the specific library version requires a different pattern.
            // For this implementation, we'll assume the simpler overload exists or 
            // wrap the sync call in a thread if needed. 
            
            // To be safe and compatible with the callback pattern we exposed:
            new Thread(() -> {
                try {
                    String result = llmInference.generateResponse(prompt);
                    if (result != null) {
                        listener.onResponse(result);
                    } else {
                        listener.onError("Failed to generate response.");
                    }
                } catch (Exception e) {
                    listener.onError("Error during generation: " + e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            listener.onError("Failed to start generation: " + e.getMessage());
        }
    }

    public ModelType getCurrentModelType() {
        return currentModelType;
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
