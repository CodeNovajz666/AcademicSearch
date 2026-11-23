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

<<<<<<< HEAD
    /**
     * 限流/瞬时错误的自动重试：上传后台处理会连续触发 embedding + 知识抽取，
     * 免费档位极易撞上 429（Throttling.RateQuota），一次失败不应让整篇文献的知识抽取报废。
     * 策略与 agent-python/llm.py 一致：优先服务端 Retry-After，否则 5s/15s/30s 指数退避。
     */
    private static final int MAX_ATTEMPTS = 4;

=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
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
<<<<<<< HEAD
        // DashScope embedding 接口要求 input 为 {"texts": [...]}，直接传数组会 400 IllegalInput
        body.put("input", Map.of("texts", texts));
=======
        body.put("input", texts);
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
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

<<<<<<< HEAD
    public String chatRaw(String model, String systemPrompt, String userContent) {
        return chatRaw(model, systemPrompt, userContent, 2048);
    }

    /**
     * 单轮原生对话（无工具），maxTokens 可调。对话模型（qwen3.8-flash）仅在
     * OpenAI 兼容端点提供（chat-endpoint 指向 compatible-mode/v1/chat/completions），
     * 因此这里使用 OpenAI 报文格式而非 DashScope 原生 input/parameters 格式。
     * 默认 2048 够 RAG 回答；知识抽取等多字段 JSON 输出需要更大预算，
     * 否则 JSON 被截断成非法文本导致解析失败（表现为"知识要点抽取无结果"）。
     */
    public String chatRaw(String model, String systemPrompt, String userContent, int maxTokens) {
=======
    /** 单轮原生对话（无工具） */
    public String chatRaw(String model, String systemPrompt, String userContent) {
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
        requireApiKey();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
<<<<<<< HEAD
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)));
        // qwen3.8 为思考模型：经典 RAG / 知识抽取这类单轮调用关闭思考，控制延迟并保住输出预算
        body.put("enable_thinking", false);
        body.put("temperature", 0.7);
        body.put("max_tokens", maxTokens);
=======
        body.put("input", Map.of("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent))));

        Map<String, Object> parameters = new HashMap<>();
        // 不传该参数时可能返回 output.text 而非 output.choices[].message 结构
        parameters.put("result_format", "message");
        parameters.put("temperature", 0.7);
        parameters.put("max_tokens", 2048);
        body.put("parameters", parameters);
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

        String responseBody = post(config.getChatEndpoint(), body);
        JsonNode root = parseOrThrow(responseBody);

<<<<<<< HEAD
        JsonNode message = root.path("choices").path(0).path("message");
=======
        JsonNode message = root.path("output").path("choices").path(0).path("message");
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
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

<<<<<<< HEAD
        long backoffMs = 5000;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try (Response response = okHttpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    return responseBody;
                }
                if (isRetryable(response.code()) && attempt < MAX_ATTEMPTS) {
                    long waitMs = retryAfterMs(response.header("Retry-After"), backoffMs);
                    log.warn("DashScope API {} (attempt {}/{}), retrying in {} ms",
                            response.code(), attempt, MAX_ATTEMPTS, waitMs);
                    sleepBackoff(waitMs);
                    backoffMs *= 3;
                    continue;
                }
                throw new DashScopeApiException("DashScope API " + response.code() + ": " + truncate(responseBody, 300));
            } catch (IOException e) {
                // 网络抖动同样值得重试：退避后重建连接
                if (attempt < MAX_ATTEMPTS) {
                    log.warn("DashScope IO error (attempt {}/{}): {}, retrying in {} ms",
                            attempt, MAX_ATTEMPTS, e.getMessage(), backoffMs);
                    sleepBackoff(backoffMs);
                    backoffMs *= 3;
                    continue;
                }
                throw new DashScopeApiException("Failed to call DashScope API", e);
            }
        }
        throw new DashScopeApiException("DashScope API failed after " + MAX_ATTEMPTS + " attempts");
    }

    /** 429 限流与 5xx 服务端错误值得重试；4xx 参数类错误重试无意义。 */
    private static boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    /** 服务端给出 Retry-After 时优先遵循（上限 60s），否则用传入的退避值。 */
    private static long retryAfterMs(String retryAfterHeader, long fallbackMs) {
        if (retryAfterHeader == null || retryAfterHeader.isBlank()) {
            return fallbackMs;
        }
        try {
            return Math.min((long) (Double.parseDouble(retryAfterHeader.trim()) * 1000), 60_000L);
        } catch (NumberFormatException e) {
            return fallbackMs;
        }
    }

    private static void sleepBackoff(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DashScopeApiException("Interrupted while waiting for DashScope retry");
=======
        try (Response response = okHttpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new DashScopeApiException("DashScope API " + response.code() + ": " + truncate(responseBody, 300));
            }
            return responseBody;
        } catch (IOException e) {
            throw new DashScopeApiException("Failed to call DashScope API", e);
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
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
