
package com.atak.plugins.mlsnapshots.services;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.GenerativeModel;

public class AIService {

    private final GenerativeModel model;

    public AIService(String projectId, String location, String modelName) {
        try (VertexAI vertexAI = new VertexAI(projectId, location)) {
            this.model = new GenerativeModel(modelName, vertexAI);
        }
    }

    public String generateContent(String prompt) {
        try {
            GenerateContentResponse response = model.generateContent(prompt);
            return response.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }
}
