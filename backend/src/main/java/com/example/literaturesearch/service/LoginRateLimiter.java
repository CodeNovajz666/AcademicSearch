package com.example.literaturesearch.service;

import com.example.literaturesearch.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流：同一邮箱在窗口内连续失败达到上限后锁定一段时间，防止在线撞库；
 * 另按来源 IP 计失败总数（阈值更宽），防止攻击者绕过邮箱维度无限尝试或恶意锁死他人邮箱。
 * 内存实现，仅适用于单实例部署（与 CaptchaService 同一约束）。
 */
@Service
public class LoginRateLimiter {

    static final int MAX_FAILURES = 5;
    static final int MAX_FAILURES_PER_IP = 30;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<String, Attempts> attemptsByEmail = new ConcurrentHashMap<>();
    private final Map<String, Attempts> attemptsByIp = new ConcurrentHashMap<>();

    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** 登录前调用：邮箱或来源 IP 已锁定则直接拒绝，不做密码校验。 */
    public void assertNotLocked(String email, String ip) {
        purgeExpired();
        if (email != null && !email.isBlank()) {
            String emailKey = email.trim().toLowerCase();
            Attempts attempts = attemptsByEmail.get(emailKey);
            if (attempts != null && attempts.isLocked(clock.instant(), MAX_FAILURES)) {
                throw new BusinessException(
                        "登录失败次数过多，账号已临时锁定，请 " + minutesUntilUnlock(emailKey) + " 分钟后再试");
            }
        }
        if (ip != null && !ip.isBlank()) {
            Attempts attempts = attemptsByIp.get(ipKey(ip));
            if (attempts != null && attempts.isLocked(clock.instant(), MAX_FAILURES_PER_IP)) {
                throw new BusinessException("当前网络登录失败次数过多，请 15 分钟后再试");
            }
        }
    }

    /** 密码校验失败后调用：按邮箱与来源 IP 双维度累计失败次数。 */
    public void recordFailure(String email, String ip) {
        if (email != null && !email.isBlank()) {
            bumpFailure(attemptsByEmail, email.trim().toLowerCase());
        }
        if (ip != null && !ip.isBlank()) {
            bumpFailure(attemptsByIp, ipKey(ip));
        }
    }

    /** 登录成功后调用：清除该邮箱的失败记录（IP 维度不清，继续约束撞库源）。 */
    public void reset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        attemptsByEmail.remove(email.trim().toLowerCase());
    }

    private static String ipKey(String ip) {
        return "ip:" + ip.trim().toLowerCase();
    }

    private void bumpFailure(Map<String, Attempts> map, String key) {
        map.compute(key, (k, old) -> {
            Instant now = clock.instant();
            if (old == null || old.isExpired(now)) {
                return new Attempts(now);
            }
            old.count++;
            old.lastFailureAt = now;
            return old;
        });
    }

    private long minutesUntilUnlock(String emailKey) {
        Attempts attempts = attemptsByEmail.get(emailKey);
        long seconds = attempts == null ? 0
                : Duration.between(clock.instant(), attempts.unlockAt()).toSeconds();
        // 向上取整，避免显示“0 分钟后可试”但实际仍被拒绝
        return Math.max(1, (seconds + 59) / 60);
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        attemptsByEmail.values().removeIf(a -> a.isExpired(now) && !a.isLocked(now, MAX_FAILURES));
        attemptsByIp.values().removeIf(a -> a.isExpired(now) && !a.isLocked(now, MAX_FAILURES_PER_IP));
    }

    private static final class Attempts {
        int count = 1;
        Instant lastFailureAt;
        final Instant firstFailureAt;

        Attempts(Instant firstFailureAt) {
            this.firstFailureAt = firstFailureAt;
            this.lastFailureAt = firstFailureAt;
        }

        boolean isExpired(Instant now) {
            return now.isAfter(lastFailureAt.plus(FAILURE_WINDOW));
        }

        boolean isLocked(Instant now, int maxFailures) {
            return count >= maxFailures && !now.isAfter(lastFailureAt.plus(LOCK_DURATION));
        }

        Instant unlockAt() {
            return lastFailureAt.plus(LOCK_DURATION);
        }
    }
}
