package com.example.literaturesearch.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;

/**
 * Milvus 客户端。@Lazy：启动阶段不连接，首次向量操作时才建立连接——
 * 避免 Milvus 未启动（如 docker compose 未拉起）阻塞整个后端启动。
 */
@Slf4j
@Configuration
public class MilvusConfig {

    @Lazy
    @Bean(destroyMethod = "close")
    public MilvusClientV2 milvusClient(@Value("${milvus.uri}") String uri) {
        try {
            MilvusClientV2 client = new MilvusClientV2(ConnectConfig.builder()
                    .uri(uri)
                    .connectTimeoutMs(Duration.ofSeconds(10).toMillis())
                    .build());
            log.info("Connected to Milvus at {}", uri);
            return client;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "无法连接 Milvus（" + uri + "）：请先在 milvus/ 目录执行 docker compose up -d", e);
        }
    }
}
