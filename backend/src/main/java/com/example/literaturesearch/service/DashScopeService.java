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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashScopeService {

    /**
     * 单次 embedding 请求允许的文本条数上限。
     * text-embedding-v1/v2 为 25 条，v3/v4 为 10 条，取全模型兼容的最小值。
     */
    private static final int EMBEDDING_BATCH_SIZE = 10;

    private final DashScopeConfig config;
    private final ObjectMapper objectMapper;

    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(120))
            .writeTimeout(Duration.ofSeconds(30))
            .build();

    // ==================== Embedding ====================

    public List<Double> getEmbedding(String text) {
        return getEmbeddings(List.of(text)).get(0);
    }

    public List<List<Double>> getEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        requireApiKey();

        List<List<Double>> result = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += EMBEDDING_BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + EMBEDDING_BATCH_SIZE, texts.size()));
            result.addAll(requestEmbeddings(batch));
        }
        return result;
    }

    private List<List<Double>> requestEmbeddings(List<String> texts) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", config.getModelName());
        body.put("input", texts);
        String responseBody = post(config.getEndpoint(), body);

        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException e) {
            throw new DashScopeApiException("Invalid embedding response", e);
        }

        JsonNode embeddings = rootNode.path("output").path("embeddings");
        if (!embeddings.isArray() || embeddings.isEmpty()) {
            throw new DashScopeApiException("No embeddings returned");
        }
        if (embeddings.size() != texts.size()) {
            throw new DashScopeApiException(
                    "Embeddings count mismatch: expected " + texts.size() + ", got " + embeddings.size());
        }

        // 响应按 text_index 标记对应输入位置，按输入顺序重排
        Map<Integer, List<Double>> byIndex = new HashMap<>();
        for (int i = 0; i < embeddings.size(); i++) {
            JsonNode node = embeddings.get(i);
            int index = node.hasNonNull("text_index") ? node.get("text_index").asInt() : i;
            byIndex.put(index, parseEmbedding(node.get("embedding")));
        }

        List<List<Double>> ordered = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            List<Double> vector = byIndex.get(i);
            if (vector == null) {
                throw new DashScopeApiException("Missing embedding for input index " + i);
            }
            ordered.add(vector);
        }
        return ordered;
    }

    private List<Double> parseEmbedding(JsonNode embeddingNode) {
        if (embeddingNode == null || !embeddingNode.isArray()) {
            throw new DashScopeApiException("Invalid embedding data returned");
        }
        return objectMapper.convertValue(embeddingNode,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class));
    }

    // ==================== Chat ====================

    public String chat(String prompt, String context) {
        String systemPrompt = "你是一个学术文献助手，请根据提供的文献内容回答用户的问题。" +
                "如果文献内容中没有相关信息，请明确说明。请用中文回答。";
        String fullPrompt = "文献内容：\n" + context + "\n\n问题：" + prompt;
        return chatRaw(config.getChatModelName(), systemPrompt, fullPrompt);
    }

    /** 单轮原生对话（无工具） */
    public String chatRaw(String model, String systemPrompt, String userContent) {
        requireApiKey();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", Map.of("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent))));

        Map<String, Object> parameters = new HashMap<>();
        // 不传该参数时可能返回 output.text 而非 output.choices[].message 结构
        parameters.put("result_format", "message");
        parameters.put("temperature", 0.7);
        parameters.put("max_tokens", 2048);
        body.put("parameters", parameters);

        String responseBody = post(config.getChatEndpoint(), body);
        JsonNode root = parseOrThrow(responseBody);

        JsonNode message = root.path("output").path("choices").path(0).path("message");
        if (message.isMissingNode()) {
            throw new DashScopeApiException("No message returned: " + truncate(responseBody, 300));
        }
        return message.hasNonNull("content") ? message.get("content").asText() : "";
    }

    // ==================== 通用 HTTP ====================

    private String post(String url, Map<String, Object> body) {
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new DashScopeApiException("Failed to serialize request body", e);
        }

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new DashScopeApiException("DashScope API " + response.code() + ": " + truncate(responseBody, 300));
            }
            return responseBody;
        } catch (IOException e) {
            throw new DashScopeApiException("Failed to call DashScope API", e);
        }
    }

    private JsonNode parseOrThrow(String responseBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException e) {
            throw new DashScopeApiException("Invalid response: " + truncate(responseBody, 300), e);
        }
        if (root.has("code") && root.has("message") && !root.has("output")) {
            throw new DashScopeApiException("DashScope API error: " + root.get("message").asText());
        }
        return root;
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private void requireApiKey() {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new DashScopeApiException(
                    "未配置 DashScope API Key：请设置环境变量 DASHSCOPE_API_KEY，或在项目根目录 / backend 目录创建 .env 文件（参考 .env.example）");
        }
    }
}
