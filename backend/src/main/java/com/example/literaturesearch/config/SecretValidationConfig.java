package com.example.literaturesearch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

/**
 * 敏感配置启动校验：JWT 密钥缺失或过短时直接终止启动。
 * 此前 application.yml 提供过公开的默认密钥，等于允许任何人伪造令牌；
 * 现在改为“不配置就起不来”，把问题暴露在部署时而不是被利用时。
 */
@Configuration
public class SecretValidationConfig {

    public SecretValidationConfig(@Value("${jwt.secret}") String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET 未配置：请在项目根目录 .env（参考 .env.example）或环境变量中设置，"
                            + "出于安全考虑系统不再提供默认值");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET 长度不足：HS256 签名要求至少 32 字节，建议使用 64 位以上随机字符串");
        }
    }
}
