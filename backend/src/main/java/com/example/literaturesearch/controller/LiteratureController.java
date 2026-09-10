package com.example.literaturesearch.controller;

import com.example.literaturesearch.dto.response.ApiResponse;
import com.example.literaturesearch.dto.response.LiteratureResponse;
import com.example.literaturesearch.dto.response.SearchResult;
import com.example.literaturesearch.entity.User;
import com.example.literaturesearch.service.LiteratureService;
import com.example.literaturesearch.service.VectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/literature")
@RequiredArgsConstructor
public class LiteratureController {

    private final LiteratureService literatureService;
    private final VectorService vectorService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<LiteratureResponse>> uploadLiterature(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "authors", required = false) String authors,
            @RequestParam(value = "journal", required = false) String journal,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "doi", required = false) String doi) throws IOException {

        log.info("Uploading literature: {}", file.getOriginalFilename());
        LiteratureResponse response = literatureService.uploadLiterature(file, title, authors, journal, year, doi, user.getId());
        return ResponseEntity.ok(ApiResponse.success("上传成功", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LiteratureResponse>> getLiteratureById(@PathVariable Long id) {
        LiteratureResponse response = literatureService.getLiteratureById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LiteratureResponse>>> getAllLiteratures() {
        List<LiteratureResponse> response = literatureService.getAllLiteratures();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteLiterature(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        literatureService.deleteLiterature(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success("删除成功", null));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<SearchResult>>> searchLiterature(
            @RequestParam("query") String query,
            @RequestParam(value = "topK", defaultValue = "10") int topK) {
        
        log.info("Searching literature with query: {}", query);
        List<SearchResult> results = vectorService.search(query, topK);
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}