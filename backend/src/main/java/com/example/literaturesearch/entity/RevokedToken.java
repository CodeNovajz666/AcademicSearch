package com.example.literaturesearch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 已吊销的 refresh 令牌（jti 黑名单）：登出时写入，刷新端点校验命中即拒绝。
 * 只存到令牌自身过期为止，过期条目在登出时顺带清理。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "revoked_tokens")
public class RevokedToken {

    /** JWT 的 jti claim（UUID） */
    @Id
    @Column(name = "jti", length = 64)
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
