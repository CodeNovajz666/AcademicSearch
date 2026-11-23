package com.example.literaturesearch.service;

import com.example.literaturesearch.dto.response.LiteratureResponse;
import com.example.literaturesearch.entity.Literature;
import com.example.literaturesearch.exception.BusinessException;
import com.example.literaturesearch.exception.LiteratureNotFoundException;
import com.example.literaturesearch.repository.LiteratureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureService {

    private final LiteratureRepository literatureRepository;
    private final com.example.literaturesearch.repository.UserLiteratureStateRepository stateRepository;
    private final com.example.literaturesearch.repository.UserRepository userRepository;
    private final com.example.literaturesearch.repository.NoteRepository noteRepository;
    private final VectorService vectorService;
    private final LiteratureProcessingService literatureProcessingService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    // 不加 @Transactional：方法内的 PDF 解析与 embedding 调用（外部 API，可达数十秒）
    // 不应占用数据库事务；唯一写操作 literatureRepository.save 自带事务。
    /** 社区板块（5 个，与前端 categories.js 同源约定；后续扩展再迁数据库表）。 */
    public static final List<String> CATEGORIES =
            List.of("computer-science", "engineering", "science", "medicine", "social-science");

    public LiteratureResponse uploadLiterature(MultipartFile file, String title, String authors,
                                               String journal, Integer year, String doi, Long userId,
                                               Boolean contributeToCommunity, String category) throws IOException {
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || !originalFileName.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("只支持PDF格式文件");
        }
        // originalFileName 客户端可控（如 "..\\..\\x.pdf"），必须剥离路径成分，
        // 否则存储路径可被解析到 uploads 目录之外（路径穿越写文件）
        String baseName = originalFileName.replace('\\', '/');
        baseName = baseName.substring(baseName.lastIndexOf('/') + 1).trim();
        if (baseName.isEmpty() || !baseName.toLowerCase().endsWith(".pdf")) {
            baseName = "document.pdf";
        }

        // 必须绝对路径：MultipartFile.transferTo 对相对路径会解析到 Tomcat 临时目录，导致 FileNotFoundException
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String storedFileName = System.currentTimeMillis() + "_" + baseName;
        Path filePath = uploadPath.resolve(storedFileName);
        file.transferTo(filePath.toFile());

        try {
            String content = extractTextFromPdf(filePath.toString());

            String extractedTitle = title;
            String extractedAuthors = authors;
            String extractedAbstract = extractAbstract(content);

            if (extractedTitle == null || extractedTitle.isEmpty()) {
                extractedTitle = extractTitle(content);
                if (extractedTitle == null || extractedTitle.isEmpty()) {
                    extractedTitle = baseName.replaceAll("(?i)\\.pdf$", "");
                }
            }

            Literature literature = Literature.builder()
                    .title(extractedTitle)
                    .authors(extractedAuthors)
                    .abstractText(extractedAbstract)
                    .journal(journal)
                    .year(year)
                    .doi(doi)
                    .fileName(baseName)
                    .filePath(filePath.toString())
                    .content(content)
                    .processStatus(LiteratureProcessingService.STATUS_PROCESSING)
                    // 自愿贡献制：默认私有，勾选后进入社区总知识库
                    .isPublic(Boolean.TRUE.equals(contributeToCommunity))
                    // 贡献到社区必须挂板块；私有文献板块可选
                    .category(normalizeCategory(category, contributeToCommunity))
                    .userId(userId)
                    .build();

            Literature savedLiterature = literatureRepository.save(literature);

            // 向量索引与 LLM 知识抽取在后台线程执行，上传接口立即返回；
            // 前端通过详情页轮询 processStatus 展示进度
            literatureProcessingService.submit(savedLiterature.getId(), content);

            log.info("Literature uploaded (processing async): {}", savedLiterature.getTitle());
            return LiteratureResponse.fromEntity(savedLiterature);
        } catch (Exception e) {
            // 落盘后的解析/入库失败：清掉孤儿文件再抛出，避免 uploads 目录积累无主 PDF
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException cleanupFailure) {
                log.warn("Failed to clean up uploaded file after failure: {}", filePath);
            }
            throw e;
        }
    }

    private String extractTextFromPdf(String filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(new java.io.File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * 启发式标题：取首个"非封面横幅"的合理行。
     * ICLR/arXiv 等模板的首行常是 "Published as a conference paper at ICLR 2026"
     * 这类横幅，没有检索价值，应跳过；全部横幅时退回首行兜底。
     */
    private String extractTitle(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        String fallback = null;
        for (String raw : content.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.length() <= 5 || line.length() >= 200) {
                continue;
            }
            if (fallback == null) {
                fallback = line;
            }
            if (!LiteratureProcessingService.isBannerLike(line)) {
                return line;
            }
        }
        return fallback;
    }

    /**
     * 摘要尾部悬挂的节号/页码：抽取窗口在 "introduction" 标记处切断时，
     * 下一节标题的节号（"1 Introduction" 的 "1"）会残留在摘要末尾，
     * 视觉上像被截断（". 1"）。仅在句末标点 + 空白 + 1-3 位数字时去除。
     */
    private static final java.util.regex.Pattern TRAILING_SECTION_NUMBER = java.util.regex.Pattern
            .compile("(.*[.!?。！？；;])\\s*\\d{1,3}$", java.util.regex.Pattern.DOTALL);

    static String extractAbstract(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        String lower = content.toLowerCase();
        int abstractStart = lower.indexOf("abstract");
        int markerLength = "abstract".length();

        if (abstractStart == -1) {
            abstractStart = content.indexOf("摘要");
            markerLength = "摘要".length();
        }

        if (abstractStart == -1) {
            return null;
        }

        int start = abstractStart + markerLength;
        int end = content.length();

        int keywordsStart = lower.indexOf("keywords");
        int introductionStart = lower.indexOf("introduction");

        if (keywordsStart > abstractStart) {
            end = Math.min(end, keywordsStart);
        }
        if (introductionStart > abstractStart) {
            end = Math.min(end, introductionStart);
        }

        String abstractText = content.substring(start, end).trim();
        // 去掉 "Abstract—xxx" / "摘要：xxx" 这类引导符
        while (!abstractText.isEmpty() && "：:—–-".indexOf(abstractText.charAt(0)) >= 0) {
            abstractText = abstractText.substring(1).trim();
        }

        // 去掉尾部悬挂的节号/页码（". 1"），避免看起来像被截断
        java.util.regex.Matcher matcher = TRAILING_SECTION_NUMBER.matcher(abstractText);
        if (matcher.matches()) {
            abstractText = matcher.group(1).trim();
        }

        return abstractText.isEmpty() ? null : abstractText;
    }

    /** 可见性规则：isPublic 为 NULL 视为公开（历史数据在该字段引入前就是共享的）。 */
    static boolean isPubliclyVisible(Literature literature) {
        return !Boolean.FALSE.equals(literature.getIsPublic());
    }

    /** 板块校验：公开文献必填且必须在枚举内；私有文献传非法值时清空。 */
    static String normalizeCategory(String category, Boolean contributeToCommunity) {
        boolean valid = category != null && CATEGORIES.contains(category);
        if (Boolean.TRUE.equals(contributeToCommunity) && !valid) {
            throw new BusinessException("贡献到社区时必须选择一个板块");
        }
        return valid ? category : null;
    }

    /** 私有文献仅属主可访问；越权一律 404（不泄露私有文献的存在性）。 */
    private void assertReadable(Literature literature, Long userId) {
        boolean owner = userId != null && userId.equals(literature.getUserId());
        if (!owner && !isPubliclyVisible(literature)) {
            throw new LiteratureNotFoundException(literature.getId());
        }
    }

    public LiteratureResponse getLiteratureById(Long id) {
        Literature literature = literatureRepository.findById(id)
                .orElseThrow(() -> new LiteratureNotFoundException(id));
        return LiteratureResponse.fromEntity(literature);
    }

    /** 详情 + 当前用户私有状态（收藏/已读/笔记）+ 上传者昵称。私有文献仅属主可读。 */
    public LiteratureResponse getLiteratureById(Long id, Long userId) {
        LiteratureResponse response = getLiteratureById(id);
        Literature literature = literatureRepository.findById(id).orElseThrow();
        assertReadable(literature, userId);
        attachUploaderNames(List.of(response));
        stateRepository.findByUserIdAndLiteratureId(userId, id).ifPresent(response::withState);
        return response;
    }

    /** 原文 PDF 定位结果：path 为服务器磁盘路径，fileName 为下载时的展示文件名。 */
    public record LiteratureFile(Path path, String fileName) {}

    public LiteratureFile openFile(Long id, Long userId) {
        Literature literature = literatureRepository.findById(id)
                .orElseThrow(() -> new LiteratureNotFoundException(id));
        assertReadable(literature, userId);
        Path path = Paths.get(literature.getFilePath());
        if (!Files.exists(path)) {
            throw new BusinessException("原文文件不存在或已被清理，请重新上传");
        }
        return new LiteratureFile(path, literature.getFileName());
    }

    public List<LiteratureResponse> getAllLiteratures() {
        return literatureRepository.findAll().stream()
                .map(LiteratureResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /** 全部文献 + 当前用户私有状态（一次批量查询避免 N+1） */
    public List<LiteratureResponse> getAllLiteratures(Long userId) {
        List<LiteratureResponse> responses = getAllLiteratures();
        if (responses.isEmpty()) {
            return responses;
        }
        java.util.Map<Long, com.example.literaturesearch.entity.UserLiteratureState> stateMap =
                stateRepository.findByUserId(userId).stream()
                        .collect(Collectors.toMap(
                                com.example.literaturesearch.entity.UserLiteratureState::getLiteratureId,
                                s -> s));
        for (LiteratureResponse response : responses) {
            response.withState(stateMap.get(response.getId()));
        }
        return responses;
    }

    /**
     * 知识库范围："mine" = 我的私有知识库（本人全部上传，含私有与已公开）；
     * 其他 = 总知识库（仅收录用户自愿贡献的公开文献）。全量非分页（单测与内部使用）。
     */
    public List<LiteratureResponse> getAllLiteratures(String scope, Long userId) {
        List<Literature> literatures = "mine".equals(scope) && userId != null
                ? literatureRepository.findByUserId(userId)
                : literatureRepository.findByIsPublicTrueOrIsPublicIsNull().stream()
                        .filter(LiteratureService::isPubliclyVisible)
                        .toList();
        List<LiteratureResponse> responses = literatures.stream()
                .map(LiteratureResponse::fromEntity)
                .collect(Collectors.toList());
        decorate(responses, userId);
        return responses;
    }

    /**
     * 分页列表（真分页，排序过滤由数据库完成）：
     * scope=mine 私库（可按收藏过滤）/ 其他=总库（board 非空时按板块筛选）。
     * sortBy 支持 createdAt/title/year，order 支持 desc/asc，page 从 0 计。
     */
    public com.example.literaturesearch.dto.response.PageResponse<LiteratureResponse> listLiteratures(
            String scope, String board, Long userId, boolean starred,
            String sortBy, String order, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 50);
        String sortField = switch (sortBy == null ? "" : sortBy) {
            case "title" -> "title";
            case "year" -> "year";
            default -> "createdAt";
        };
        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.by(
                "asc".equalsIgnoreCase(order) ? org.springframework.data.domain.Sort.Order.asc(sortField)
                        : org.springframework.data.domain.Sort.Order.desc(sortField));
        org.springframework.data.domain.PageRequest pageRequest =
                org.springframework.data.domain.PageRequest.of(p, s, sort);

        org.springframework.data.domain.Page<Literature> result;
        if ("mine".equals(scope) && userId != null) {
            if (starred) {
                List<Long> starredIds = stateRepository.findStarredLiteratureIds(userId);
                // in () 空列表会让 SQL 报错，收藏为空直接短路
                if (starredIds.isEmpty()) {
                    return com.example.literaturesearch.dto.response.PageResponse.empty(p, s);
                }
                result = literatureRepository.findByUserIdAndIdIn(userId, starredIds, pageRequest);
            } else {
                result = literatureRepository.findByUserId(userId, pageRequest);
            }
        } else if (board != null && !board.isBlank()) {
            // 总库按板块：非法板块直接给空结果而不是全量
            result = CATEGORIES.contains(board)
                    ? literatureRepository.findPublicByCategory(board, pageRequest)
                    : new org.springframework.data.domain.PageImpl<>(List.of(), pageRequest, 0);
        } else {
            result = literatureRepository.findPublic(pageRequest);
        }

        List<LiteratureResponse> responses = result.getContent().stream()
                .map(LiteratureResponse::fromEntity)
                .collect(Collectors.toList());
        decorate(responses, userId);
        return com.example.literaturesearch.dto.response.PageResponse.of(
                responses, result.getTotalElements(), p, s);
    }

    /** 批量补充上传者昵称与当前用户私有状态（收藏/已读/笔记），一次批量查询避免 N+1。 */
    private void decorate(List<LiteratureResponse> responses, Long userId) {
        attachUploaderNames(responses);
        if (userId == null) {
            return;
        }
        java.util.Map<Long, com.example.literaturesearch.entity.UserLiteratureState> stateMap =
                stateRepository.findByUserId(userId).stream()
                        .collect(Collectors.toMap(
                                com.example.literaturesearch.entity.UserLiteratureState::getLiteratureId,
                                st -> st));
        for (LiteratureResponse response : responses) {
            response.withState(stateMap.get(response.getId()));
        }
    }

    /** 批量补充上传者昵称（一次查询全部涉及用户，避免 N+1）。 */
    private void attachUploaderNames(List<LiteratureResponse> responses) {
        List<Long> ownerIds = responses.stream()
                .map(LiteratureResponse::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ownerIds.isEmpty()) {
            return;
        }
        java.util.Map<Long, String> nameByUser = userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(
                        com.example.literaturesearch.entity.User::getId,
                        com.example.literaturesearch.entity.User::getUsername));
        for (LiteratureResponse response : responses) {
            response.setUploaderName(nameByUser.get(response.getUserId()));
        }
    }

    /** 收藏/已读/笔记 upsert：只更新传入的字段。私有文献仅属主可操作。 */
    @Transactional
    public LiteratureResponse updateState(Long literatureId, Long userId,
                                          com.example.literaturesearch.dto.request.LiteratureStateRequest request) {
        Literature literature = literatureRepository.findById(literatureId)
                .orElseThrow(() -> new LiteratureNotFoundException(literatureId));
        assertReadable(literature, userId);
        com.example.literaturesearch.entity.UserLiteratureState state = stateRepository
                .findByUserIdAndLiteratureId(userId, literatureId)
                .orElseGet(() -> com.example.literaturesearch.entity.UserLiteratureState.builder()
                        .userId(userId)
                        .literatureId(literatureId)
                        .starred(false)
                        .build());
        if (request.getStarred() != null) {
            state.setStarred(request.getStarred());
        }
        if (request.getRead() != null) {
            state.setReadStatus(request.getRead());
        }
        if (request.getNote() != null) {
            state.setNote(request.getNote().isBlank() ? null : request.getNote());
        }
        stateRepository.save(state);
        return LiteratureResponse.fromEntity(literature).withState(state);
    }

    /** 切换可见性：公开到社区总知识库 / 设回私有。仅属主可操作。 */
    public LiteratureResponse setVisibility(Long id, Long userId, Boolean isPublic) {
        Literature literature = literatureRepository.findById(id)
                .orElseThrow(() -> new LiteratureNotFoundException(id));
        if (literature.getUserId() == null || !literature.getUserId().equals(userId)) {
            throw new BusinessException("只有上传者可以更改文献的公开状态");
        }
        literature.setIsPublic(Boolean.TRUE.equals(isPublic));
        literatureRepository.save(literature);
        // 同步 Milvus 标量字段：向量取出按新可见性重插（不重新 embedding），
        // 否则社区检索会漏掉刚公开的文献 / 私有化后仍可被总库搜到
        vectorService.syncVisibility(literature.getId(), userId, Boolean.TRUE.equals(isPublic));
        LiteratureResponse response = LiteratureResponse.fromEntity(literature);
        attachUploaderNames(List.of(response));
        return response;
    }

    /**
     * 重新解析：重跑 LLM 知识抽取（标题横幅修复/知识字段刷新）。
     * 向量已存在则不重建；仅属主可触发（历史无属主数据除外）。
     */
    public void reprocess(Long id, Long currentUserId) {
        Literature literature = literatureRepository.findById(id)
                .orElseThrow(() -> new LiteratureNotFoundException(id));
        if (literature.getUserId() != null && !literature.getUserId().equals(currentUserId)) {
            throw new BusinessException("只有上传者可以重新解析该文献");
        }
        if (literature.getContent() == null || literature.getContent().isBlank()) {
            throw new BusinessException("该文献没有可解析的正文文本");
        }
        literature.setProcessStatus(LiteratureProcessingService.STATUS_PROCESSING);
        literatureRepository.save(literature);
        literatureProcessingService.submit(id, null);
    }

    @Transactional
    public void deleteLiterature(Long id, Long currentUserId) {
        Literature literature = literatureRepository.findById(id)
                .orElseThrow(() -> new LiteratureNotFoundException(id));

        // 历史数据（user_id 为空）不做属主限制，新上传的文献仅上传者可删除
        if (literature.getUserId() != null && !literature.getUserId().equals(currentUserId)) {
            throw new BusinessException("只能删除自己上传的文献");
        }

        vectorService.deleteVectorsByLiteratureId(id);
        stateRepository.deleteByLiteratureId(id);
        // 笔记插件：文献删除时清掉其下的批注/问题/记录，避免悬空数据
        noteRepository.deleteByLiteratureId(id);
        literatureRepository.delete(literature);

        // 文件删除放在事务提交之后：若事务回滚（如向量或状态删除失败），
        // DB 记录仍在而文件已删，会造成"打开原文 404"的不一致
        Path filePath = Paths.get(literature.getFilePath());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    Files.deleteIfExists(filePath);
                } catch (IOException e) {
                    log.warn("Failed to delete file: {}", filePath);
                }
            }
        });

        log.info("Literature deleted: {}", id);
    }
}