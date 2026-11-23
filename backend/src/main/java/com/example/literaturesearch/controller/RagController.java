package com.example.literaturesearch.controller;

import com.example.literaturesearch.dto.response.ApiResponse;
import com.example.literaturesearch.dto.response.RagAnswerResponse;
import com.example.literaturesearch.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @PostMapping("/query")
    public ResponseEntity<ApiResponse<RagAnswerResponse>> query(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.example.literaturesearch.entity.User user,
            @RequestBody RagQueryRequest request) {

        // 用户问题属敏感输入：DEBUG 且截断，避免完整问题进生产日志
        log.debug("RAG query: {} scope={}", truncate(request.getQuestion()), request.getScope());
        Long ownerScope = "mine".equals(request.getScope()) && user != null ? user.getId() : null;
        RagAnswerResponse response = ragService.query(request.getQuestion(), request.getTopK(), ownerScope);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/query")
    public ResponseEntity<ApiResponse<RagAnswerResponse>> queryGet(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.example.literaturesearch.entity.User user,
            @RequestParam("question") String question,
            @RequestParam(value = "topK", defaultValue = "5") int topK,
            @RequestParam(value = "scope", required = false) String scope) {

        log.debug("RAG query (GET): {} scope={}", truncate(question), scope);
        Long ownerScope = "mine".equals(scope) && user != null ? user.getId() : null;
        RagAnswerResponse response = ragService.query(question, topK, ownerScope);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private static String truncate(String text) {
        if (text == null) return null;
        return text.length() <= 80 ? text : text.substring(0, 80) + "...";
    }

    public static class RagQueryRequest {
        private String question;
        private int topK = 5;
        /** 知识库范围：all=总知识库（默认），mine=我的私有知识库 */
        private String scope = "all";

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }
    }
}