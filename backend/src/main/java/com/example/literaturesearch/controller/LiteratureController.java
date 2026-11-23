package com.example.literaturesearch.controller;

import com.example.literaturesearch.dto.response.ApiResponse;
import com.example.literaturesearch.dto.response.LiteratureResponse;
import com.example.literaturesearch.dto.response.SearchResult;
import com.example.literaturesearch.entity.User;
import com.example.literaturesearch.service.LiteratureService;
import com.example.literaturesearch.service.VectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
<<<<<<< HEAD
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
<<<<<<< HEAD
import java.nio.charset.StandardCharsets;
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/literature")
@RequiredArgsConstructor
public class LiteratureController {

    private final LiteratureService literatureService;
    private final VectorService vectorService;
<<<<<<< HEAD
    private final com.example.literaturesearch.service.RecommendationService recommendationService;
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<LiteratureResponse>> uploadLiterature(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "authors", required = false) String authors,
            @RequestParam(value = "journal", required = false) String journal,
            @RequestParam(value = "year", required = false) Integer year,
<<<<<<< HEAD
            @RequestParam(value = "doi", required = false) String doi,
            @RequestParam(value = "isPublic", required = false, defaultValue = "false") Boolean isPublic,
            @RequestParam(value = "category", required = false) String category) throws IOException {

        log.info("Uploading literature: {} isPublic={} category={}", file.getOriginalFilename(), isPublic, category);
        LiteratureResponse response = literatureService.uploadLiterature(
                file, title, authors, journal, year, doi, user.getId(), isPublic, category);
=======
            @RequestParam(value = "doi", required = false) String doi) throws IOException {

        log.info("Uploading literature: {}", file.getOriginalFilename());
        LiteratureResponse response = literatureService.uploadLiterature(file, title, authors, journal, year, doi, user.getId());
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
        return ResponseEntity.ok(ApiResponse.success("上传成功", response));
    }

    @GetMapping("/{id}")
<<<<<<< HEAD
    public ResponseEntity<ApiResponse<LiteratureResponse>> getLiteratureById(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        LiteratureResponse response = literatureService.getLiteratureById(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 收藏/已读/笔记 upsert：body 三个字段均可选，只更新传入字段。 */
    @PutMapping("/{id}/state")
    public ResponseEntity<ApiResponse<LiteratureResponse>> updateState(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody com.example.literaturesearch.dto.request.LiteratureStateRequest request) {
        LiteratureResponse response = literatureService.updateState(id, user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("状态已更新", response));
    }

    /**
     * 打开/下载原文 PDF。预览默认 inline（浏览器内嵌查看），?download=true 时 attachment 触发保存。
     * 返回二进制流而非 ApiResponse 包裹：前端以 blob 接收，避免 base64 膨胀。
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<org.springframework.core.io.Resource> openLiteratureFile(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam(value = "download", required = false, defaultValue = "false") boolean download) {
        LiteratureService.LiteratureFile file = literatureService.openFile(id, user.getId());
        ContentDisposition disposition = ContentDisposition
                .builder(download ? "attachment" : "inline")
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(new FileSystemResource(file.path()));
    }

    /**
     * 分页列表：scope=mine 私库（可 starred=true 只看收藏）/ 默认总库；board 按社区板块筛选（仅总库）。
     * 排序 sortBy=createdAt|title|year，order=desc|asc，page 从 0 计，size 默认 12（上限 50）。
     */
    @GetMapping
    public ResponseEntity<ApiResponse<com.example.literaturesearch.dto.response.PageResponse<LiteratureResponse>>> getAllLiteratures(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "board", required = false) String board,
            @RequestParam(value = "starred", required = false, defaultValue = "false") boolean starred,
            @RequestParam(value = "sortBy", required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "order", required = false, defaultValue = "desc") String order,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "12") int size) {
        var response = literatureService.listLiteratures(
                scope, board, user.getId(), starred, sortBy, order, page, size);
=======
    public ResponseEntity<ApiResponse<LiteratureResponse>> getLiteratureById(@PathVariable Long id) {
        LiteratureResponse response = literatureService.getLiteratureById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LiteratureResponse>>> getAllLiteratures() {
        List<LiteratureResponse> response = literatureService.getAllLiteratures();
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteLiterature(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        literatureService.deleteLiterature(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success("删除成功", null));
    }

<<<<<<< HEAD
    /** 切换可见性：公开到社区总知识库 / 设回私有（仅属主）。 */
    @PutMapping("/{id}/visibility")
    public ResponseEntity<ApiResponse<LiteratureResponse>> setVisibility(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody com.example.literaturesearch.dto.request.VisibilityRequest request) {
        LiteratureResponse response = literatureService.setVisibility(id, user.getId(), request.getIsPublic());
        return ResponseEntity.ok(ApiResponse.success(
                Boolean.TRUE.equals(request.getIsPublic()) ? "已公开到社区总知识库" : "已设为私有", response));
    }

    /** 重新解析：重跑 LLM 知识抽取并修复标题误判（如封面横幅），后台异步执行。 */
    @PostMapping("/{id}/reprocess")
    public ResponseEntity<ApiResponse<Void>> reprocess(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        literatureService.reprocess(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success("已提交重新解析", null));
    }

    /**
     * 社区推荐：以当前用户文献内容为画像，在他人公开文献中做相似度召回。
     * 无需 query 参数，结果包含 basedOn（参与计算的我的文献标题）与 items。
     */
    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<com.example.literaturesearch.dto.response.RecommendationResponse>> recommend(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "topK", defaultValue = "6") int topK) {
        return ResponseEntity.ok(ApiResponse.success(
                recommendationService.recommend(user.getId(), topK)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<SearchResult>>> searchLiterature(
            @AuthenticationPrincipal User user,
            @RequestParam("query") String query,
            @RequestParam(value = "topK", defaultValue = "10") int topK,
            @RequestParam(value = "scope", required = false) String scope) {

        // 检索词属敏感输入：DEBUG 且截断，避免完整查询进生产日志
        log.debug("Searching literature with query: {} scope={}",
                query != null && query.length() > 80 ? query.substring(0, 80) + "..." : query, scope);
        Long ownerScope = "mine".equals(scope) && user != null ? user.getId() : null;
        List<SearchResult> results = vectorService.search(query, topK, ownerScope);
=======
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<SearchResult>>> searchLiterature(
            @RequestParam("query") String query,
            @RequestParam(value = "topK", defaultValue = "10") int topK) {
        
        log.info("Searching literature with query: {}", query);
        List<SearchResult> results = vectorService.search(query, topK);
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}