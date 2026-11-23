package com.example.literaturesearch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class WebConfig {

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:4173}")
    private List<String> allowedOrigins;

    /**
     * 交给 Spring Security 的 http.cors() 消费（见 SecurityConfig）：
     * CORS 处理位于安全过滤链内部，401/403 等被安全层短路写出的响应也会带 CORS 头，
     * 前端才能读到 401 并触发 refresh 续期。
     * 独立注册的 CorsFilter bean 排序在安全链之后，短路响应拿不到 CORS 头，故不采用。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setExposedHeaders(List.of("Content-Disposition"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
