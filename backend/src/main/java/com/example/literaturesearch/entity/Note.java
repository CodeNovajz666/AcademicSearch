package com.example.literaturesearch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 研究工作区笔记插件：用户在某篇文献下产生的笔记内容。
 *
 * type 三类（与前端约定一致）：
 * - annotation：原文批注（可选选区锚点高亮 rects / 角标 badge；citeText 非空表示引用标记，
 *   前端会从引用处引出箭头指向页边旗标）；
 * - question：用户自己的问题；
 * - record：用户自己的记录。
 *
 * 页面几何信息（rects/badge）以百分比坐标 JSON 存字符串：换算已在选区发生时完成，
 * 后端只做透明存储，不解析语义。page 为 null 表示不锚定页码（question/record 常见）。
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ws_note",
        indexes = @Index(name = "idx_ws_note_user_lit", columnList = "user_id, literature_id"))
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "literature_id", nullable = false)
    private Long literatureId;

    /** annotation / question / record */
    @Column(name = "type", nullable = false, length = 16)
    private String type;

    /** 锚定页码（null = 不锚定页码） */
    @Column(name = "page_no")
    private Integer page;

    @Column(name = "quote", columnDefinition = "TEXT")
    private String quote;

    /** 选区矩形数组 JSON：[{l,t,w,h}, ...]，单位为页面容器的百分比 */
    @Column(name = "rects_json", columnDefinition = "TEXT")
    private String rectsJson;

    /** 角标位置 JSON：{l,t}，单位为页面容器的百分比 */
    @Column(name = "badge_json", length = 128)
    private String badgeJson;

    /** 引用标记：被标记的引文原文（如 "[12]"），非空时前端引出箭头 */
    @Column(name = "cite_text", length = 500)
    private String citeText;

    /** 批注色板 key：yellow/green/blue/pink */
    @Column(name = "color", length = 16)
    private String color;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (type == null || type.isBlank()) type = "annotation";
        if (color == null || color.isBlank()) color = "yellow";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
