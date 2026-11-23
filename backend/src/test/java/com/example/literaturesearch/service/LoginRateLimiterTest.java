package com.example.literaturesearch.service;

import com.example.literaturesearch.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    @Test
    void underLimit_failuresDoNotLock() {
        LoginRateLimiter limiter = new LoginRateLimiter(clockAt(START));
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES - 1; i++) {
            limiter.recordFailure("user@test.com", null);
        }
        assertDoesNotThrow(() -> limiter.assertNotLocked("user@test.com", null));
    }

    @Test
    void reachLimit_locksAndRejectsBeforePasswordCheck() {
        LoginRateLimiter limiter = new LoginRateLimiter(clockAt(START));
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure("user@test.com", null);
        }
        BusinessException ex = assertThrows(BusinessException.class,
                () -> limiter.assertNotLocked("user@test.com", null));
        assertTrue(ex.getMessage().contains("锁定"));
    }

    @Test
    void lockExpires_afterLockDuration() {
        MutableClock clock = new MutableClock(START);
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure("user@test.com", null);
        }
        clock.advance(LoginRateLimiter.LOCK_DURATION.plusSeconds(1));
        assertDoesNotThrow(() -> limiter.assertNotLocked("user@test.com", null));
    }

    @Test
    void reset_clearsHistoryAfterSuccess() {
        LoginRateLimiter limiter = new LoginRateLimiter(clockAt(START));
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES - 1; i++) {
            limiter.recordFailure("user@test.com", null);
        }
        limiter.reset("user@test.com");
        // 重新累计：成功登录清零后，再次失败不应直接触发锁定
        limiter.recordFailure("user@test.com", null);
        assertDoesNotThrow(() -> limiter.assertNotLocked("user@test.com", null));
    }

    @Test
    void emailKeyIsCaseInsensitive() {
        LoginRateLimiter limiter = new LoginRateLimiter(clockAt(START));
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure("User@Test.com", null);
        }
        assertThrows(BusinessException.class, () -> limiter.assertNotLocked("user@test.com", null));
    }

    @Test
    void failureWindowSliding_expiredFailuresIgnored() {
        MutableClock clock = new MutableClock(START);
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        // 窗口边缘的旧失败：过了窗口后新失败应重新计数
        limiter.recordFailure("user@test.com", null);
        clock.advance(LoginRateLimiter.FAILURE_WINDOW.plusSeconds(1));
        limiter.recordFailure("user@test.com", null);
        clock.advance(LoginRateLimiter.FAILURE_WINDOW.plusSeconds(1));
        assertDoesNotThrow(() -> limiter.assertNotLocked("user@test.com", null));
    }

    @Test
    void ipFailures_reachHigherThresholdBeforeLock() {
        LoginRateLimiter limiter = new LoginRateLimiter(clockAt(START));
        // 超过邮箱阈值但未达 IP 阈值：同一 IP 换不同邮箱仍可登录
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES_PER_IP - 1; i++) {
            limiter.recordFailure("user" + i + "@test.com", "10.0.0.1");
        }
        assertDoesNotThrow(() -> limiter.assertNotLocked("fresh@test.com", "10.0.0.1"));
    }

    @Test
    void ipFailures_lockBlocksNewEmailsFromSameIp() {
        LoginRateLimiter limiter = new LoginRateLimiter(clockAt(START));
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES_PER_IP; i++) {
            limiter.recordFailure("user" + i + "@test.com", "10.0.0.1");
        }
        BusinessException ex = assertThrows(BusinessException.class,
                () -> limiter.assertNotLocked("fresh@test.com", "10.0.0.1"));
        assertTrue(ex.getMessage().contains("网络"));
        // 其他 IP 不受影响
        assertDoesNotThrow(() -> limiter.assertNotLocked("fresh@test.com", "10.0.0.2"));
    }

    @Test
    void ipLock_expiresAfterLockDuration() {
        MutableClock clock = new MutableClock(START);
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES_PER_IP; i++) {
            limiter.recordFailure("user" + i + "@test.com", "10.0.0.1");
        }
        clock.advance(LoginRateLimiter.LOCK_DURATION.plusSeconds(1));
        assertDoesNotThrow(() -> limiter.assertNotLocked("fresh@test.com", "10.0.0.1"));
    }

    /** 可手动推进时间的时钟，模拟窗口流逝。 */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(java.time.Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
