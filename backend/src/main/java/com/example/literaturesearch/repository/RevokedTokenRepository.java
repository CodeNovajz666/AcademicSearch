package com.example.literaturesearch.repository;

import com.example.literaturesearch.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    /** 清理已过期的吊销记录（黑名单只需覆盖未过期的令牌） */
    long deleteByExpiresAtBefore(Instant now);
}
