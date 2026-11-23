package com.example.literaturesearch.service;

import com.example.literaturesearch.dto.request.NoteRequest;
import com.example.literaturesearch.dto.response.NoteResponse;
import com.example.literaturesearch.entity.Literature;
import com.example.literaturesearch.entity.Note;
import com.example.literaturesearch.exception.BusinessException;
import com.example.literaturesearch.exception.LiteratureNotFoundException;
import com.example.literaturesearch.repository.LiteratureRepository;
import com.example.literaturesearch.repository.NoteRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 笔记插件服务：工作区批注/引用标记/问题/记录的持久化与查询。
 *
 * 笔记是用户私有数据：所有查询都带 userId 条件，修改/删除前先校验属主。
 * 创建笔记要求目标文献对当前用户可见（自己的或公开的），与"打开原文"同一规则。
 * rects/badge 属于前端渲染几何，后端只做 JSON 透明存储，不做语义校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteService {

    static final Set<String> NOTE_TYPES = Set.of("annotation", "question", "record");
    static final Set<String> NOTE_COLORS = Set.of("yellow", "green", "blue", "pink");

    private static final int MAX_CONTENT_LENGTH = 20000;
    private static final int MAX_QUOTE_LENGTH = 2000;
    private static final int MAX_CITE_LENGTH = 500;
    private static final int MAX_RECTS = 30;

    private final NoteRepository noteRepository;
    private final LiteratureRepository literatureRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public NoteResponse create(Long userId, NoteRequest request) {
        if (request.getLiteratureId() == null) {
            throw new BusinessException("缺少 literatureId");
        }
        Literature literature = visibleLiterature(request.getLiteratureId(), userId);

        Note note = Note.builder()
                .userId(userId)
                .literatureId(literature.getId())
                .type(normalizeType(request.getType()))
                .page(normalizePage(request.getPage()))
                .quote(validateLength(trimToNull(request.getQuote()), MAX_QUOTE_LENGTH, "引文"))
                .rectsJson(toJson(validateRects(request.getRects())))
                .badgeJson(toJson(request.getBadge()))
                .citeText(validateLength(trimToNull(request.getCiteText()), MAX_CITE_LENGTH, "引文标记"))
                .color(normalizeColor(request.getColor()))
                .content(validateLength(trimToNull(request.getContent()), MAX_CONTENT_LENGTH, "笔记内容"))
                .build();
        note = noteRepository.save(note);
        return toResponse(note);
    }

    @Transactional
    public NoteResponse update(Long userId, Long noteId, NoteRequest request) {
        Note note = noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new BusinessException("笔记不存在或无权修改"));

        if (request.getType() != null) note.setType(normalizeType(request.getType()));
        if (request.getPage() != null) note.setPage(normalizePage(request.getPage()));
        if (request.getContent() != null) {
            note.setContent(validateLength(trimToNull(request.getContent()), MAX_CONTENT_LENGTH, "笔记内容"));
        }
        if (request.getColor() != null) note.setColor(normalizeColor(request.getColor()));
        if (request.getCiteText() != null) {
            note.setCiteText(validateLength(trimToNull(request.getCiteText()), MAX_CITE_LENGTH, "引文标记"));
        }
        // 锚点几何只在明确传入时更新（编辑文字/换色不应触碰选区）
        if (request.getRects() != null) note.setRectsJson(toJson(validateRects(request.getRects())));
        if (request.getBadge() != null) note.setBadgeJson(toJson(request.getBadge()));

        return toResponse(note);
    }

    @Transactional
    public void delete(Long userId, Long noteId) {
        Note note = noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new BusinessException("笔记不存在或无权删除"));
        noteRepository.delete(note);
    }

    /** 某篇文献下的全部笔记（工作区加载） */
    @Transactional(readOnly = true)
    public List<NoteResponse> listByLiterature(Long userId, Long literatureId) {
        if (literatureId == null) {
            throw new BusinessException("缺少 literatureId");
        }
        Literature literature = visibleLiterature(literatureId, userId);
        String title = literature.getTitle();
        return noteRepository.findByUserIdAndLiteratureIdOrderByIdDesc(userId, literatureId)
                .stream()
                .map(note -> toResponse(note, title))
                .collect(Collectors.toList());
    }

    /** 笔记插件页：用户全部笔记，附带文献标题 */
    @Transactional(readOnly = true)
    public List<NoteResponse> listAll(Long userId) {
        List<Note> notes = noteRepository.findByUserIdOrderByIdDesc(userId);
        if (notes.isEmpty()) return List.of();

        List<Long> litIds = notes.stream().map(Note::getLiteratureId).distinct().toList();
        Map<Long, String> titles = literatureRepository.findAllById(litIds).stream()
                .collect(Collectors.toMap(Literature::getId, Literature::getTitle, (a, b) -> a));
        return notes.stream()
                .map(note -> toResponse(note, titles.get(note.getLiteratureId())))
                .collect(Collectors.toList());
    }

    // ---- 内部工具 ----

    private Literature visibleLiterature(Long literatureId, Long userId) {
        Literature literature = literatureRepository.findById(literatureId)
                .orElseThrow(() -> new LiteratureNotFoundException(literatureId));
        boolean owner = literature.getUserId() != null && literature.getUserId().equals(userId);
        if (!owner && !LiteratureService.isPubliclyVisible(literature)) {
            throw new LiteratureNotFoundException(literatureId);
        }
        return literature;
    }

    private NoteResponse toResponse(Note note) {
        return toResponse(note, literatureRepository.findById(note.getLiteratureId())
                .map(Literature::getTitle)
                .orElse(null));
    }

    private NoteResponse toResponse(Note note, String literatureTitle) {
        return NoteResponse.builder()
                .id(note.getId())
                .literatureId(note.getLiteratureId())
                .literatureTitle(literatureTitle)
                .type(note.getType())
                .page(note.getPage())
                .quote(note.getQuote())
                .rects(fromJsonList(note.getRectsJson()))
                .badge(fromJsonMap(note.getBadgeJson()))
                .citeText(note.getCiteText())
                .color(note.getColor())
                .content(note.getContent())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }

    private String normalizeType(String type) {
        String t = trimToNull(type);
        if (t == null) return "annotation";
        if (!NOTE_TYPES.contains(t)) {
            throw new BusinessException("不支持的笔记类型: " + t);
        }
        return t;
    }

    private String normalizeColor(String color) {
        String c = trimToNull(color);
        if (c == null) return "yellow";
        if (!NOTE_COLORS.contains(c)) {
            throw new BusinessException("不支持的批注颜色: " + c);
        }
        return c;
    }

    private Integer normalizePage(Integer page) {
        if (page == null) return null;
        if (page < 1 || page > 100000) {
            throw new BusinessException("页码不合法");
        }
        return page;
    }

    private List<Map<String, Object>> validateRects(List<Map<String, Object>> rects) {
        if (rects == null) return null;
        if (rects.size() > MAX_RECTS) {
            throw new BusinessException("选区矩形数量过多（最多 " + MAX_RECTS + " 个）");
        }
        return rects;
    }

    private String validateLength(String value, int max, String field) {
        if (value == null) return null;
        if (value.length() > max) {
            throw new BusinessException(field + "过长（最多 " + max + " 个字符）");
        }
        return value;
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("批注几何数据序列化失败");
        }
    }

    private List<Map<String, Object>> fromJsonList(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("笔记选区 JSON 解析失败，按无选区处理: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> fromJsonMap(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("笔记角标 JSON 解析失败，按无角标处理: {}", e.getMessage());
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
