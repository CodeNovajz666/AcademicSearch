package com.example.literaturesearch.controller;

import com.example.literaturesearch.dto.request.NoteRequest;
import com.example.literaturesearch.dto.response.ApiResponse;
import com.example.literaturesearch.dto.response.NoteResponse;
import com.example.literaturesearch.entity.User;
import com.example.literaturesearch.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 笔记插件接口：工作区批注（含引用标记箭头）/问题/记录的 CRUD。
 * 全部按当前登录用户隔离；不带 literatureId 的列表供笔记插件页聚合展示。
 */
@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    /** literatureId 缺省时返回用户全部笔记（笔记插件页），否则返回该文献下的笔记（工作区）。 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<NoteResponse>>> listNotes(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "literatureId", required = false) Long literatureId) {
        List<NoteResponse> data = literatureId != null
                ? noteService.listByLiterature(user.getId(), literatureId)
                : noteService.listAll(user.getId());
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NoteResponse>> createNote(
            @AuthenticationPrincipal User user,
            @RequestBody NoteRequest request) {
        NoteResponse response = noteService.create(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("笔记已保存", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NoteResponse>> updateNote(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody NoteRequest request) {
        NoteResponse response = noteService.update(user.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("笔记已更新", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNote(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        noteService.delete(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("笔记已删除", null));
    }
}
