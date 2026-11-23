package com.example.literaturesearch.exception;

/** 认证缺失（如未携带令牌访问需要登录态的接口），映射为 401 而非 500。 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
