package com.example.literaturesearch.exception;

public class LiteratureNotFoundException extends RuntimeException {
    public LiteratureNotFoundException(Long id) {
        super("文献不存在: " + id);
    }

    public LiteratureNotFoundException(String message) {
        super(message);
    }
}