package com.example.literaturesearch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "literature")
public class Literature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "authors", length = 1000)
    private String authors;

    // MEDIUMTEXT：取消摘要字数限制后，TEXT 的 64KB 字节上限可能不够
    @Column(name = "abstract_text", columnDefinition = "MEDIUMTEXT")
    private String abstractText;

    @Column(name = "journal", length = 200)
    private String journal;

    @Column(name = "year")
    private Integer year;

    @Column(name = "doi", length = 200)
    private String doi;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    /** 后台处理状态：PROCESSING / READY / FAILED（历史数据为 NULL，视为 READY） */
    @Column(name = "process_status", length = 20)
    private String processStatus;

    /** LLM 抽取：主要内容概述 */
    @Column(name = "main_content", columnDefinition = "TEXT")
    private String mainContent;

    /** LLM 抽取：研究方法与操作步骤 */
    @Column(name = "methods", columnDefinition = "TEXT")
    private String methods;

    /** LLM 抽取：实验设置与结果 */
    @Column(name = "experiments", columnDefinition = "TEXT")
    private String experiments;

    /** LLM 抽取：结论 */
    @Column(name = "conclusions", columnDefinition = "TEXT")
    private String conclusions;

    @Column(name = "user_id")
    private Long userId;

    /**
     * 可见性：true = 已贡献到社区总知识库（全员可检索/阅读）；
     * false = 仅本人可见。历史数据为 NULL，视为已公开（它们在此字段引入前就是共享的）。
     */
    @Column(name = "is_public")
    private Boolean isPublic;

    /** 社区板块（computer-science/engineering/science/medicine/social-science），私有文献可为空 */
    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}