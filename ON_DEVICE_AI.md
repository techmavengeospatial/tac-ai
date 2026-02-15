
# On-Device AI Feature

This document outlines the architecture and usage of the on-device AI feature, which enables offline, real-time text generation within the application.

## 1. Overview

The application now includes an on-device Large Language Model (LLM) for AI-powered text generation. This feature replaces the previous reliance on the online Google Vertex AI service, offering several key advantages:

- **Offline Capability:** The AI model runs directly on the device, requiring no internet connection.
- **Low Latency:** Responses are generated locally, reducing network-related delays.
- **Data Privacy:** Prompts and generated text are not sent to external servers.

This is powered by the TensorFlow Lite library and the MediaPipe GenAI toolkit.

## 2. Core Component: `AIService`

The central component for this feature is the `AIService` class (`app/src/main/java/com/atak/plugins/mlsnapshots/services/AIService.java`). This service is responsible for loading the on-device model and handling text generation requests.

### Initialization

The `AIService` must be initialized with an Android `Context`:

```java
// From AtakPlugin.java
aiService = new AIService(context);
```

### Asynchronous Text Generation

To avoid blocking the main application thread, AI requests are handled asynchronously. The `generateContent` method takes a `prompt` and a `ResponseListener` callback.

```java
public interface ResponseListener {
    void onResponse(String response);
    void onError(String error);
}

public void generateContent(String prompt, ResponseListener listener) { ... }
```

This non-blocking design is crucial for maintaining a responsive user interface.

## 3. Setup and Configuration

To use the on-device AI, you must add a compatible TensorFlow Lite (`.tflite`) model to the project.

### Step 1: Get a Model

Download a pre-trained `.tflite` language model. A good starting point is the **Gemma 2B** model, which can be found on the TensorFlow Hub or Hugging Face.

*Example Model:* `gemma-2b-it-cpu-int4.tflite`

### Step 2: Add the Model to the Project

1.  Create an `assets` directory under `app/src/main/`.
2.  Place the downloaded `.tflite` model file inside this directory.

    The final path should be: `app/src/main/assets/gemma-2b-it-cpu-int4.tflite`

### Step 3: Configure the Model Path

The `AIService` specifies the model to load via the `MODEL_PATH` constant. Ensure this path matches the filename of the model you added to the `assets` folder.

```java
// In AIService.java
private static final String MODEL_PATH = "gemma-2b-it-cpu-int4.tflite";
```

## 4. Usage Example

The `AtakPlugin.java` class demonstrates how to use the `AIService`. A prompt is sent to the service, and the response is handled in the `ResponseListener` callback.

```java
// From AtakPlugin.java's onSnapshotReady method

String prompt = "Give me a summary of the current tactical situation based on available data.";
Log.d(TAG, "Sending prompt to AI for analysis: " + prompt);

aiService.generateContent(prompt, new AIService.ResponseListener() {
    @Override
    public void onResponse(String response) {
        // The UI must be updated on the main thread
        mapView.post(() -> {
            Log.d(TAG, "AI Analysis Result: " + response);
            // Display the result in a UI element
        });
    }

    @Override
    public void onError(String error) {
        mapView.post(() -> {
            Log.e(TAG, "AI Error: " + error);
        });
    }
});
```

## 5. Dependencies

This feature relies on the TensorFlow Lite task library for text, which is included in the `app/build.gradle` file:

```groovy
implementation 'org.tensorflow:tensorflow-lite-task-text:0.4.0'
```
