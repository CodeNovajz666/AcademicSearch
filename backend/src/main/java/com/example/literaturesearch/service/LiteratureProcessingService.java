package com.example.literaturesearch.service;

import com.example.literaturesearch.entity.Literature;
import com.example.literaturesearch.repository.LiteratureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 文献后台处理：上传接口立即返回后，在工作线程中依次完成
 * 向量索引构建与 LLM 知识抽取，并将 process_status 置为 READY/FAILED。
 *
 * 为什么不放在上传请求里：50MB 级 PDF 会产生数百个分块（几十次 embedding 调用）
 * 外加一次长文本 LLM 抽取，同步执行必然逼近浏览器/代理超时；
 * 状态字段 + 前端轮询可让上传即时完成、解析进度可见。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureProcessingService {

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";

    private final LiteratureRepository literatureRepository;
    private final VectorService vectorService;
    private final KnowledgeExtractionService knowledgeExtractionService;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "literature-processing");
        thread.setDaemon(true);
        return thread;
    });

    public void submit(Long literatureId, String content) {
        executor.submit(() -> process(literatureId, content));
    }

    /**
     * 启动自检：上一进程可能在处理中途被杀（断电/重启），那些文献的 process_status
     * 会永远停在 PROCESSING，前端每 3 秒的轮询就永无止境。重置为 FAILED，
     * 用户可在详情页用「重新解析」触发恢复。
     */
    @PostConstruct
    void resetStuckProcessing() {
        try {
            int reset = literatureRepository.resetStuckProcessing();
            if (reset > 0) {
                log.warn("启动时把 {} 篇卡在 PROCESSING 的文献重置为 FAILED（可在详情页重新解析）", reset);
            }
        } catch (Exception e) {
            log.warn("启动时重置 PROCESSING 状态失败（DB 尚未就绪？）: {}", e.getMessage());
        }
    }

    /**
     * 启动修复：历史上被旧摘要上限（3000/8000 字）截断的文献，重启时自动重新提取补全，
     * 无需逐篇手动调用 reprocess。与 reprocess 的摘要语义一致（refreshAbstract：结果不同即覆盖），
     * 但不动向量索引与知识字段——它们从未受摘要上限影响。
     *
     * 在 ApplicationReadyEvent 触发：晚于 SchemaMigrationRunner 的列升级，
     * 补全后的长摘要不会写回旧的 TEXT 列。幂等（与存量一致则跳过），
     * 异步执行不阻塞启动。
     */
    @EventListener(ApplicationReadyEvent.class)
    void repairHistoricalAbstracts() {
        // 串行执行：两个修复任务都按行读改写，并行会用旧快照互相覆盖
        executor.submit(() -> {
            repairAbstracts();
            classifyUntagged();
        });
    }

    private void repairAbstracts() {
        try {
            int repaired = 0;
            int page = 0;
            Page<Literature> slice;
            // 按 id 排序分页：修复过程伴随写入，无序分页在 MySQL 下可能跳行
            do {
                slice = literatureRepository.findAll(PageRequest.of(page, 50, Sort.by("id")));
                for (Literature literature : slice.getContent()) {
                    if (repairAbstractOf(literature)) {
                        repaired++;
                    }
                }
                page++;
            } while (slice.hasNext());
            if (repaired > 0) {
                log.info("历史摘要修复完成：{} 篇已补全", repaired);
            }
        } catch (Exception e) {
            log.warn("历史摘要修复失败（不影响运行）: {}", e.getMessage());
        }
    }

    /** 单篇摘要修复。返回是否发生了写入。 */
    private boolean repairAbstractOf(Literature literature) {
        // 后台任务正在处理的文献由其自身刷新，避免并发写同一行
        if (STATUS_PROCESSING.equals(literature.getProcessStatus())) {
            return false;
        }
        String content = literature.getContent();
        if (content == null || content.isEmpty()) {
            return false;
        }
        String before = literature.getAbstractText();
        refreshAbstract(literature, content);
        if (Objects.equals(before, literature.getAbstractText())) {
            return false;
        }
        literatureRepository.save(literature);
        return true;
    }

    /**
     * 板块回填：category 是后加的字段，历史文献与私有上传（上传表单仅在贡献社区时
     * 显示板块选择器）都是 NULL，前端卡片因此没有学科标签。
     * 用轻量 LLM 分类逐篇补打；写入走原子 UPDATE（只在仍为空时落库），
     * 与摘要修复/后台处理并发时不互相覆盖。模型判不出的（返回 null）留给下次启动重试。
     */
    private void classifyUntagged() {
        try {
            List<Literature> untagged =
                    literatureRepository.findByProcessStatusAndCategoryIsNull(STATUS_READY);
            if (untagged.isEmpty()) {
                return;
            }
            log.info("发现 {} 篇未打板块标签的文献，开始自动分类", untagged.size());
            int tagged = 0;
            for (Literature literature : untagged) {
                String content = literature.getContent();
                if (content == null || content.isBlank()) {
                    continue;
                }
                try {
                    String key = knowledgeExtractionService.classify(content);
                    if (key != null
                            && literatureRepository.backfillCategory(literature.getId(), key) > 0) {
                        tagged++;
                    }
                } catch (Exception e) {
                    log.warn("自动分类失败 literatureId={}: {}", literature.getId(), e.getMessage());
                }
            }
            log.info("板块自动分类完成：{}/{} 篇已补打标签", tagged, untagged.size());
        } catch (Exception e) {
            log.warn("板块自动分类任务失败（不影响运行）: {}", e.getMessage());
        }
    }

    private void process(Long literatureId, String content) {
        Literature literature = literatureRepository.findById(literatureId).orElse(null);
        // 上传后随即被删除的竞态：直接放弃
        if (literature == null) {
            return;
        }
        try {
            if (content == null || content.isBlank()) {
                content = literature.getContent();
            }
            if (content != null && !content.isEmpty()) {
                refreshAbstract(literature, content);
                // 向量已存在（重新解析场景）时跳过重建，避免重复插入
                if (vectorService.countByLiteratureId(literatureId) == 0) {
                    try {
                        vectorService.createVectorIndex(literature, content);
                    } catch (Exception e) {
                        log.error("向量索引构建失败 literatureId={}: {}", literatureId, e.getMessage(), e);
                    }
                }

                KnowledgeExtractionService.Knowledge knowledge =
                        knowledgeExtractionService.extract(content);
                applyKnowledge(literature, knowledge);
            }
            literature.setProcessStatus(STATUS_READY);
        } catch (Exception e) {
            log.error("文献后台处理失败 literatureId={}: {}", literatureId, e.getMessage(), e);
            literature.setProcessStatus(STATUS_FAILED);
        }
        literatureRepository.save(literature);
        log.info("文献后台处理完成 literatureId={} status={}", literatureId, literature.getProcessStatus());
    }

    /**
     * 摘要刷新：历史数据可能被旧的上限（3000/8000 字）截断，或残留节号（". 1"）。
     * 提取结果与存量不同即覆盖（新算法已取消字数上限 + 节号剥离，结果只会更完整）；
     * 首次处理时与上传阶段同一算法、结果等值，不会产生多余写入。
     */
    private void refreshAbstract(Literature literature, String content) {
        String fresh = LiteratureService.extractAbstract(content);
        if (fresh == null) {
            return;
        }
        String current = literature.getAbstractText();
        if (current == null || current.isBlank() || !fresh.equals(current)) {
            log.info("Abstract refreshed for literature {}: {} -> {} chars",
                    literature.getId(), current == null ? 0 : current.length(), fresh.length());
            literature.setAbstractText(fresh);
        }
    }

    /** 把 LLM 抽取结果应用到文献：覆盖知识四字段；标题仅修复"封面横幅"型误判；摘要/板块缺失时补齐。 */
    static void applyKnowledge(Literature literature, KnowledgeExtractionService.Knowledge knowledge) {
        if (knowledge == null) {
            return;
        }
        literature.setMainContent(knowledge.mainContent());
        literature.setMethods(knowledge.methods());
        literature.setExperiments(knowledge.experiments());
        literature.setConclusions(knowledge.conclusions());
        if (isBannerLike(literature.getTitle()) && !isBlank(knowledge.title())) {
            literature.setTitle(truncate(knowledge.title(), 500));
        }
        if (isBlank(literature.getAbstractText()) && !isBlank(knowledge.abstractText())) {
            literature.setAbstractText(knowledge.abstractText());
        }
        // 板块仅在为空时采纳：公开贡献的板块是用户明确选择的，不允许被模型改写
        if (isBlank(literature.getCategory()) && !isBlank(knowledge.category())
                && LiteratureService.CATEGORIES.contains(knowledge.category())) {
            literature.setCategory(knowledge.category());
        }
    }

    /**
     * 启发式标题抽取常抓到论文封面横幅（如 "Published as a conference paper at ICLR 2026"），
     * 这类标题没有检索价值，允许被 LLM 抽取的真实标题覆盖。
     */
    static boolean isBannerLike(String title) {
        if (title == null) {
            return false;
        }
        return title.strip().toLowerCase()
                .matches("^(published|accepted|proceedings|preprint|under review|arxiv|appeared|accepted as|conference|journal|workshop|submitted|copyright|provided|licensed|licence|permission|\\d+(st|nd|rd|th) ).*");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
