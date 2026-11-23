package com.example.literaturesearch.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.generator.RandomGenerator;
import com.example.literaturesearch.dto.response.CaptchaResponse;
import com.example.literaturesearch.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 图形验证码：内存池存储，5 分钟有效、一次性使用、连错 5 次作废。
 * 单实例部署（当前架构为单机 Spring Boot），无需分布式存储。
 */
@Service
public class CaptchaService {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 5;

    private final Clock clock;
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public CaptchaService() {
        this(Clock.systemUTC());
    }

    CaptchaService(Clock clock) {
        this.clock = clock;
    }

    public CaptchaResponse generate() {
        purgeExpired();
        var captcha = CaptchaUtil.createCircleCaptcha(130, 44, 4, 2);
        // 去掉易混淆字符（i/l/o/0/1），统一小写，校验时忽略大小写
        captcha.setGenerator(new RandomGenerator("abcdefghjkmnpqrstuvwxyz23456789", 4));
        captcha.createCode();
        String captchaId = UUID.randomUUID().toString();
        store.put(captchaId, new Entry(captcha.getCode(), clock.instant().plus(TTL)));
        return CaptchaResponse.builder()
                .captchaId(captchaId)
                .image(captcha.getImageBase64Data())
                .build();
    }

    // synchronized：同一验证码的并发校验串行化，保证"一次性消费"与连错计数不被竞态绕过
    public synchronized void validate(String captchaId, String inputCode) {
        if (captchaId == null || captchaId.isBlank() || inputCode == null || inputCode.isBlank()) {
            throw new BusinessException("请输入验证码");
        }
        Entry entry = store.get(captchaId);
        if (entry == null || clock.instant().isAfter(entry.expireAt)) {
            store.remove(captchaId);
            throw new BusinessException("验证码已过期，请刷新后重试");
        }
        if (!entry.code.equalsIgnoreCase(inputCode.trim())) {
            if (entry.attempts.incrementAndGet() >= MAX_ATTEMPTS) {
                store.remove(captchaId);
                throw new BusinessException("验证码错误次数过多，请刷新验证码");
            }
            throw new BusinessException("验证码错误");
        }
        store.remove(captchaId);
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        store.values().removeIf(e -> now.isAfter(e.expireAt));
    }

    /** 仅供单元测试窥探答案码 */
    String peekCode(String captchaId) {
        Entry entry = store.get(captchaId);
        return entry == null ? null : entry.code;
    }

    private static final class Entry {
        final String code;
        final Instant expireAt;
        final AtomicInteger attempts = new AtomicInteger();

        Entry(String code, Instant expireAt) {
            this.code = code;
            this.expireAt = expireAt;
        }
    }
}
