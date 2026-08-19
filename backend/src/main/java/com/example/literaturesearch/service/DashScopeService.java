package com.example.literaturesearch.service;

import com.example.literaturesearch.config.DashScopeConfig;
import com.example.literaturesearch.exception.DashScopeApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashScopeService {

    private final DashScopeConfig config;
    private final ObjectMapper objectMapper;
    private final OkHttpClient okHttpClient = new OkHttpClient();

    public List<Double> getEmbedding(String text) {
        String requestBody;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getModelName());
            body.put("input", text);
            requestBody = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new DashScopeApiException("Failed to serialize request body", e);
        }

        Request request = new Request.Builder()
                .url(config.getEndpoint())
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new DashScopeApiException("API request failed: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode rootNode = objectMapper.readTree(responseBody);
            
            if (rootNode.has("error")) {
                String errorMsg = rootNode.get("error").get("message").asText();
                throw new DashScopeApiException("API error: " + errorMsg);
            }

            JsonNode embeddings = rootNode.get("output").get("embeddings");
            if (embeddings == null || embeddings.isEmpty()) {
                throw new DashScopeApiException("No embeddings returned");
            }

            JsonNode embeddingNode = embeddings.get(0).get("embedding");
            return objectMapper.convertValue(embeddingNode, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class));
        } catch (IOException e) {
            throw new DashScopeApiException("Failed to call DashScope API", e);
        }
    }

    public String chat(String prompt, String context) {
        String systemPrompt = "你是一个学术文献助手，请根据提供的文献内容回答用户的问题。" +
                "如果文献内容中没有相关信息，请明确说明。请用中文回答。";
        
        String fullPrompt = "文献内容：\n" + context + "\n\n问题：" + prompt;

        String requestBody;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getChatModelName());
            
            Map<String, Object> input = new HashMap<>();
            input.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", fullPrompt)
            ));
            
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("temperature", 0.7);
            parameters.put("max_tokens", 2048);
            
            body.put("input", input);
            body.put("parameters", parameters);
            
            requestBody = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new DashScopeApiException("Failed to serialize request body", e);
        }

        Request request = new Request.Builder()
                .url(config.getChatEndpoint())
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new DashScopeApiException("Chat API request failed: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode rootNode = objectMapper.readTree(responseBody);
            
            if (rootNode.has("error")) {
                String errorMsg = rootNode.get("error").get("message").asText();
                throw new DashScopeApiException("Chat API error: " + errorMsg);
            }

            JsonNode output = rootNode.get("output");
            if (output == null) {
                throw new DashScopeApiException("No output returned");
            }

            JsonNode choices = output.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new DashScopeApiException("No choices returned");
            }

            return choices.get(0).get("message").get("content").asText();
        } catch (IOException e) {
            throw new DashScopeApiException("Failed to call DashScope Chat API", e);
        }
    }
}