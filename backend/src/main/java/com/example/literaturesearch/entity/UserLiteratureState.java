package com.example.literaturesearch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户对文献的私有状态（收藏/已读/笔记）。
 * 文献库全体用户共享，此表保存"每个用户自己的"维度，(user_id, literature_id) 唯一。
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_literature_state",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "literature_id"}))
public class UserLiteratureState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "literature_id", nullable = false)
    private Long literatureId;

    @Column(name = "starred", nullable = false)
    private Boolean starred;

    /** 已读标记（null = 未读） */
    @Column(name = "read_status")
    private Boolean readStatus;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
        if (starred == null) starred = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
