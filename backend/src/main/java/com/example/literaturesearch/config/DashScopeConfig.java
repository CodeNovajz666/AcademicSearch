package com.example.literaturesearch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "aliyun.dashscope")
public class DashScopeConfig {
    private String apiKey;
    private String endpoint;
    private String chatEndpoint;
    private String modelName;
    private String chatModelName;
}