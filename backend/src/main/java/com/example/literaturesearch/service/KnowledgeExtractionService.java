package com.example.literaturesearch.service;

import com.example.literaturesearch.config.DashScopeConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * LLM 结构化知识抽取：从论文全文中提炼主要内容 / 研究方法 / 实验 / 结论。
 * 输入取全文头部（含摘要与方法）+ 尾部（含实验与结论），单次调用 qwen 生成 JSON。
 * 任何解析失败都返回空结果并交由调用方容错（知识字段留空不影响文献入库与检索）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeExtractionService {

    private static final int HEAD_CHARS = 8000;
    private static final int TAIL_CHARS = 4000;
    /**
     * 输出 token 预算：四个长字段 + 标题/摘要原文，默认 2048 会把 JSON 截断成
     * 非法文本（解析必然失败，表现为"知识要点抽取无结果"），因此单独调大。
     */
    private static final int MAX_OUTPUT_TOKENS = 4096;
    /** 解析失败重试次数：LLM 偶发抽风/截断，重试一次通常可恢复 */
    private static final int MAX_ATTEMPTS = 2;

    /** 分类输出上限：只回一个枚举 key，16 token 足够。 */
    private static final int CLASSIFY_MAX_OUTPUT_TOKENS = 16;
    /** 分类输入截断：判断学科只需标题/摘要/方法头部，无需全文。 */
    private static final int CLASSIFY_HEAD_CHARS = 4000;

    private final DashScopeService dashScopeService;
    private final DashScopeConfig config;
    private final ObjectMapper objectMapper;

    /**
     * 抽取结果。title/abstractText 仅在调用方判定启发式抽取缺失时才采用；
     * category 为学科板块 key（五选一），调用方仅在文献板块为空时采纳。
     */
    public record Knowledge(String title, String abstractText,
                            String mainContent, String methods,
                            String experiments, String conclusions,
                            String category) {}

    private static final String SYSTEM_PROMPT = """
            你是学术论文解析引擎。阅读给定的论文文本（可能是全文的开头与结尾拼接，中间可能省略），\
            只输出一个 JSON 对象，不要输出任何解释、前后缀或代码块标记。JSON 字段：
            {"main_content": "主要内容概述，150-300字，说明论文解决什么问题、提出什么方案",
             "methods": "研究方法与具体操作步骤，如何实现/使用，150-400字",
             "experiments": "实验设置、数据集、对比基线与关键结果，100-300字，没有实验则写'本文不含实验'",
             "conclusions": "主要结论与贡献，100-200字",
             "title": "论文标题（若文本中可确定）",
             "abstract": "论文摘要原文（若文本中可确定）",
             "category": "学科板块，只能从 computer-science、engineering、science、medicine、social-science 中选一个最贴切的，无法判断填 null"}
            所有字段均为字符串，信息不足时填 null，禁止编造。""";

    public Knowledge extract(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String payload = buildPayload(content);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String output = dashScopeService.chatRaw(
                        config.getChatModelName(), SYSTEM_PROMPT, payload, MAX_OUTPUT_TOKENS);
                Knowledge knowledge = parse(output);
                if (knowledge != null && hasKnowledgeFields(knowledge)) {
                    return knowledge;
                }
                // 输出不可解析或四个知识字段全为 null（模型认为信息不足）：
                // 重试一次；仍失败则留空，不影响文献入库与检索
                log.warn("知识抽取第 {}/{} 次输出无效（{}），{}",
                        attempt, MAX_ATTEMPTS,
                        knowledge == null ? "不可解析" : "知识字段全空",
                        attempt < MAX_ATTEMPTS ? "重试" : "放弃（文献入库不受影响）");
            } catch (Exception e) {
                log.warn("知识抽取第 {}/{} 次调用失败: {}",
                        attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    log.error("知识抽取失败（文献入库不受影响）");
                }
            }
        }
        return null;
    }

    private static boolean hasKnowledgeFields(Knowledge k) {
        return k.mainContent() != null || k.methods() != null
                || k.experiments() != null || k.conclusions() != null;
    }

    String buildPayload(String content) {
        String text = content.strip();
        String body = text.length() <= HEAD_CHARS + TAIL_CHARS
                ? text
                : text.substring(0, HEAD_CHARS) + "\n\n...(中间部分省略)...\n\n"
                        + text.substring(text.length() - TAIL_CHARS);
        return "论文文本：\n" + body + "\n\n请按要求只输出 JSON。";
    }

    /** 容错解析：剥离代码块围栏与前后缀文本，定位最外层 JSON 对象。 */
    Knowledge parse(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return null;
        }
        String text = llmOutput.strip();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            log.warn("知识抽取输出不含 JSON 对象: {}", abbreviate(text));
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(text.substring(start, end + 1));
            return new Knowledge(
                    textValue(root, "title"),
                    textValue(root, "abstract"),
                    textValue(root, "main_content"),
                    textValue(root, "methods"),
                    textValue(root, "experiments"),
                    textValue(root, "conclusions"),
                    textValue(root, "category"));
        } catch (Exception e) {
            log.warn("知识抽取 JSON 解析失败: {}", abbreviate(text));
            return null;
        }
    }

    private static final String CLASSIFY_SYSTEM_PROMPT = """
            你是学术论文分类器。根据论文文本判断所属学科板块，只输出以下五个 key 之一：\
            computer-science、engineering、science、medicine、social-science。\
            无法判断时只输出 unknown。禁止输出解释、标点或其他任何内容。""";

    /**
     * 轻量学科分类：只返回板块 key（不在枚举内 / unknown / 调用失败一律返回 null）。
     * 与 extract 分开：存量回填只需要一个词的输出，不必带着知识抽取的 4096 token 预算调用。
     */
    public String classify(String content) {
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            return null;
        }
        String body = text.length() <= CLASSIFY_HEAD_CHARS
                ? text : text.substring(0, CLASSIFY_HEAD_CHARS);
        try {
            String output = dashScopeService.chatRaw(
                    config.getChatModelName(), CLASSIFY_SYSTEM_PROMPT,
                    "论文文本：\n" + body, CLASSIFY_MAX_OUTPUT_TOKENS);
            // 容错剥引号/句点/空白等包装字符，只留小写字母与连字符
            String key = output == null ? "" : output.strip().toLowerCase()
                    .replaceAll("[^a-z-]", "");
            return LiteratureService.CATEGORIES.contains(key) ? key : null;
        } catch (Exception e) {
            log.warn("学科分类调用失败: {}", e.getMessage());
            return null;
        }
    }

    private static String textValue(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.asText().strip();
        return value.isEmpty() || "null".equalsIgnoreCase(value) ? null : value;
    }

    private static String abbreviate(String text) {
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }
}
