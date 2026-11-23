package com.example.literaturesearch.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动期 schema 迁移：Hibernate ddl-auto=update 只会新建表/列，
 * 不会修改已有列的类型，列类型升级必须在这里自行补一次 ALTER。
 *
 * 摘要取消字数上限后，abstract_text 需从 TEXT（64KB 字节）升级为 MEDIUMTEXT（16MB），
 * 否则超长摘要写入报 "Data too long"。新库由 Hibernate 按实体定义直接建成 MEDIUMTEXT，
 * 旧库在此自动升级，无需手工执行 SQL。
 *
 * 幂等：列已是 MEDIUMTEXT 时跳过，重复启动零成本。
 * 顺序：ApplicationRunner 晚于 Hibernate 建表、早于 ApplicationReadyEvent
 * （LiteratureProcessingService 的历史摘要修复依赖该顺序，避免超长摘要写回 TEXT 列）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            String dataType = jdbcTemplate.queryForObject(
                    "SELECT DATA_TYPE FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'literature' "
                            + "AND COLUMN_NAME = 'abstract_text'",
                    String.class);
            if ("mediumtext".equalsIgnoreCase(dataType)) {
                return;
            }
            log.info("abstract_text 列当前为 {}，升级为 MEDIUMTEXT（取消摘要字数限制的配套迁移）", dataType);
            // MODIFY 显式保留可空属性（原列无 NOT NULL）；TEXT→MEDIUMTEXT 需重建表，数据量大时稍慢
            jdbcTemplate.execute("ALTER TABLE literature MODIFY abstract_text MEDIUMTEXT NULL");
            log.info("abstract_text 列已升级为 MEDIUMTEXT");
        } catch (EmptyResultDataAccessException e) {
            log.warn("未找到 literature.abstract_text 列，跳过迁移");
        } catch (Exception e) {
            // 迁移失败不阻断启动：原有长度内的摘要仍可正常写入
            log.warn("abstract_text 迁移失败（不影响启动）: {}", e.getMessage());
        }
    }
}
