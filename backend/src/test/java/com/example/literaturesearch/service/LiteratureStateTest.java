package com.example.literaturesearch.service;

import com.example.literaturesearch.dto.request.LiteratureStateRequest;
import com.example.literaturesearch.dto.response.LiteratureResponse;
import com.example.literaturesearch.entity.Literature;
import com.example.literaturesearch.entity.UserLiteratureState;
import com.example.literaturesearch.exception.LiteratureNotFoundException;
import com.example.literaturesearch.repository.LiteratureRepository;
import com.example.literaturesearch.repository.UserLiteratureStateRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiteratureStateTest {

    private final LiteratureRepository literatureRepository = mock(LiteratureRepository.class);
    private final UserLiteratureStateRepository stateRepository = mock(UserLiteratureStateRepository.class);
    private final com.example.literaturesearch.repository.UserRepository userRepository =
            mock(com.example.literaturesearch.repository.UserRepository.class);
    private final LiteratureService service =
            new LiteratureService(literatureRepository, stateRepository, userRepository, null, null);

    private Literature literature() {
        return Literature.builder().id(1L).title("T").fileName("f.pdf").filePath("x").build();
    }

    private LiteratureStateRequest req(Boolean starred, Boolean read, String note) {
        LiteratureStateRequest r = new LiteratureStateRequest();
        r.setStarred(starred);
        r.setRead(read);
        r.setNote(note);
        return r;
    }

    @Test
    void updateState_newState_createdWithFields() {
        when(literatureRepository.findById(1L)).thenReturn(Optional.of(literature()));
        when(stateRepository.findByUserIdAndLiteratureId(9L, 1L)).thenReturn(Optional.empty());

        LiteratureResponse resp = service.updateState(1L, 9L, req(true, true, "我的笔记"));
        assertTrue(resp.getStarred());
        assertTrue(resp.getRead());
        assertEquals("我的笔记", resp.getNote());
    }

    @Test
    void updateState_partial_keepsExistingFields() {
        when(literatureRepository.findById(1L)).thenReturn(Optional.of(literature()));
        UserLiteratureState existing = UserLiteratureState.builder()
                .id(5L).userId(9L).literatureId(1L).starred(true).readStatus(true).note("旧笔记").build();
        when(stateRepository.findByUserIdAndLiteratureId(9L, 1L)).thenReturn(Optional.of(existing));

        // 只改收藏：其余字段保留
        LiteratureResponse resp = service.updateState(1L, 9L, req(false, null, null));
        assertFalse(resp.getStarred());
        assertTrue(resp.getRead());
        assertEquals("旧笔记", resp.getNote());
    }

    @Test
    void updateState_blankNote_clearedToNull() {
        when(literatureRepository.findById(1L)).thenReturn(Optional.of(literature()));
        UserLiteratureState existing = UserLiteratureState.builder()
                .id(5L).userId(9L).literatureId(1L).starred(false).note("旧笔记").build();
        when(stateRepository.findByUserIdAndLiteratureId(9L, 1L)).thenReturn(Optional.of(existing));

        LiteratureResponse resp = service.updateState(1L, 9L, req(null, null, "   "));
        assertNull(resp.getNote());
    }

    @Test
    void updateState_literatureMissing_throwsNotFound() {
        when(literatureRepository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(LiteratureNotFoundException.class, () -> service.updateState(2L, 9L, req(true, null, null)));
    }

    @Test
    void getAllLiteratures_attachesUserStates() {
        Literature lit1 = Literature.builder().id(1L).title("A").fileName("a.pdf").filePath("x").build();
        Literature lit2 = Literature.builder().id(2L).title("B").fileName("b.pdf").filePath("y").build();
        when(literatureRepository.findAll()).thenReturn(java.util.List.of(lit1, lit2));
        when(stateRepository.findByUserId(9L)).thenReturn(java.util.List.of(
                UserLiteratureState.builder().userId(9L).literatureId(1L).starred(true).readStatus(true).build()));

        var list = service.getAllLiteratures(9L);
        assertTrue(list.get(0).getStarred());
        assertTrue(list.get(0).getRead());
        assertNull(list.get(1).getStarred());
    }

    @Test
    void scopeMine_onlyReturnsOwnLiteratures() {
        Literature mine = Literature.builder().id(1L).title("Mine").userId(9L).fileName("m.pdf").filePath("x").build();
        when(literatureRepository.findByUserId(9L)).thenReturn(java.util.List.of(mine));

        // "mine" 范围走 findByUserId，绝不能落入 findAll
        var list = service.getAllLiteratures("mine", 9L);
        assertEquals(1, list.size());
        assertEquals("Mine", list.get(0).getTitle());
        assertEquals(9L, list.get(0).getUserId());
    }

    @Test
    void buildLiteratureIdFilter_formatsMilvusExpression() {
        assertEquals("literature_id in [4,6]", VectorService.buildLiteratureIdFilter(java.util.List.of(4L, 6L)));
    }
}
