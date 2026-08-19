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
            @RequestBody RagQueryRequest request) {
        
        log.info("RAG query: {}", request.getQuestion());
        RagAnswerResponse response = ragService.query(request.getQuestion(), request.getTopK());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/query")
    public ResponseEntity<ApiResponse<RagAnswerResponse>> queryGet(
            @RequestParam("question") String question,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {
        
        log.info("RAG query (GET): {}", question);
        RagAnswerResponse response = ragService.query(question, topK);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    public static class RagQueryRequest {
        private String question;
        private int topK = 5;

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
    }
}