package com.example.atak.services;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.ChatSession;
import com.google.cloud.vertexai.generativeai.GenerativeModel;

public class AIService {

    private final GenerativeModel geminiModel;
    private final ChatSession chatSession;

    public AIService() {
        // Best practice: retrieve sensitive information from environment variables
        String projectId = System.getenv("GOOGLE_CLOUD_PROJECT_ID");
        if (projectId == null || projectId.isEmpty()) {
            throw new IllegalStateException("Environment variable GOOGLE_CLOUD_PROJECT_ID is not set. Please set it to your Google Cloud Project ID.");
        }

        String location = "us-central1";
        String modelName = "gemini-1.0-pro";

        try (VertexAI vertexAI = new VertexAI(projectId, location)) {
            geminiModel = new GenerativeModel(modelName, vertexAI);
            chatSession = new ChatSession(geminiModel);
        } catch (Exception e) {
            // It's good practice to wrap the original exception
            throw new RuntimeException("Failed to initialize AI service. Ensure your environment is authenticated.", e);
        }
    }

    /**
     * Generates content from a single prompt. This is a stateless operation.
     *
     * @param prompt The text prompt to send to the model.
     * @return The generated text response.
     */
    public String generateContent(String prompt) {
        try {
            GenerateContentResponse response = geminiModel.generateContent(prompt);
            // Extract the text content from the response
            return response.getCandidates(0).getContent().getParts(0).getText();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error generating content: " + e.getMessage();
        }
    }

    /**
     * Sends a message in a multi-turn chat session. This is a stateful operation.
     *
     * @param message The message to send to the chat.
     * @return The model's response.
     */
    public String sendMessage(String message) {
        try {
            GenerateContentResponse response = chatSession.sendMessage(message);
            return response.getCandidates(0).getContent().getParts(0).getText();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error sending message: " + e.getMessage();
        }
    }
}
