package com.example.literaturesearch.exception;

public class DashScopeApiException extends RuntimeException {
    public DashScopeApiException(String message) {
        super(message);
    }

    public DashScopeApiException(String message, Throwable cause) {
        super(message, cause);
    }
}