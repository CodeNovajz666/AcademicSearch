package com.example.literaturesearch.service;

import com.example.literaturesearch.dto.response.CaptchaResponse;
import com.example.literaturesearch.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class CaptchaServiceTest {

    private final MutableClock clock = new MutableClock();
    private final CaptchaService captchaService = new CaptchaService(clock);

    @Test
    void validate_correctCode_passesAndIsOneShot() {
        CaptchaResponse captcha = captchaService.generate();
        String code = captchaService.peekCode(captcha.getCaptchaId());

        assertDoesNotThrow(() -> captchaService.validate(captcha.getCaptchaId(), code));

        // 一次性：校验成功后同 id 再用（即使答案正确）必须失败
        BusinessException ex = assertThrows(BusinessException.class,
                () -> captchaService.validate(captcha.getCaptchaId(), code));
        assertEquals("验证码已过期，请刷新后重试", ex.getMessage());
    }

    @Test
    void validate_wrongCode_fiveFailuresVoidsCaptcha() {
        CaptchaResponse captcha = captchaService.generate();
        String captchaId = captcha.getCaptchaId();

        // 前 4 次错误：普通错误提示
        for (int i = 0; i < 4; i++) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> captchaService.validate(captchaId, "zzzz"));
            assertEquals("验证码错误", ex.getMessage());
        }

        // 第 5 次错误：作废并提示刷新
        BusinessException fifth = assertThrows(BusinessException.class,
                () -> captchaService.validate(captchaId, "zzzz"));
        assertEquals("验证码错误次数过多，请刷新验证码", fifth.getMessage());

        // 作废后即使输入正确答案也无法通过
        String code = captchaService.peekCode(captchaId);
        assertNull(code);
        BusinessException after = assertThrows(BusinessException.class,
                () -> captchaService.validate(captchaId, "anycode"));
        assertEquals("验证码已过期，请刷新后重试", after.getMessage());
    }

    @Test
    void validate_expiredCaptcha_rejected() {
        CaptchaResponse captcha = captchaService.generate();
        String code = captchaService.peekCode(captcha.getCaptchaId());
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> captchaService.validate(captcha.getCaptchaId(), code));
        assertEquals("验证码已过期，请刷新后重试", ex.getMessage());
    }

    @Test
    void validate_caseInsensitiveAndTrimsInput() {
        CaptchaResponse captcha = captchaService.generate();
        String code = captchaService.peekCode(captcha.getCaptchaId());

        assertDoesNotThrow(() -> captchaService.validate(captcha.getCaptchaId(), "  " + code.toUpperCase() + "  "));
    }

    @Test
    void validate_blankInput_rejected() {
        CaptchaResponse captcha = captchaService.generate();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> captchaService.validate(captcha.getCaptchaId(), "  "));
        assertEquals("请输入验证码", ex.getMessage());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.now();

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
