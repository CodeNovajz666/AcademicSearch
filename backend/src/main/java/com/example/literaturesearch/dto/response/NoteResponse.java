package com.example.literaturesearch.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 笔记响应。rects/badge 已从存储的 JSON 反序列化为结构化对象，
 * 前端拿到即可直接渲染，无需再解析。
 */
@Data
@Builder
public class NoteResponse {

    private Long id;

    private Long literatureId;

    /** 笔记插件页展示用（按文献分组）；工作区单文献场景也一并返回 */
    private String literatureTitle;

    /** annotation / question / record */
    private String type;

    private Integer page;

    private String quote;

    private List<Map<String, Object>> rects;

    private Map<String, Object> badge;

    private String citeText;

    private String color;

    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
