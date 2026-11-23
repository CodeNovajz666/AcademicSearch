package com.example.literaturesearch.service;

import com.example.literaturesearch.entity.Literature;
import com.example.literaturesearch.exception.LiteratureNotFoundException;
import com.example.literaturesearch.repository.LiteratureRepository;
import com.example.literaturesearch.repository.UserLiteratureStateRepository;
import com.example.literaturesearch.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiteratureVisibilityTest {

    private final LiteratureRepository literatureRepository = mock(LiteratureRepository.class);
    private final UserLiteratureStateRepository stateRepository = mock(UserLiteratureStateRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final LiteratureService service =
            new LiteratureService(literatureRepository, stateRepository, userRepository, null, null);

    private Literature lit(Long ownerId, Boolean isPublic) {
        return Literature.builder().id(1L).title("T").userId(ownerId).isPublic(isPublic)
                .fileName("f.pdf").filePath("x").build();
    }

    @Test
    void visibilityRule_nullMeansPublic_legacyData() {
        assertTrue(LiteratureService.isPubliclyVisible(lit(1L, null)));
        assertTrue(LiteratureService.isPubliclyVisible(lit(1L, true)));
        assertFalse(LiteratureService.isPubliclyVisible(lit(1L, false)));
    }

    @Test
    void privateLiterature_hiddenFromOtherUsers() {
        when(literatureRepository.findById(1L)).thenReturn(Optional.of(lit(7L, false)));
        assertThrows(LiteratureNotFoundException.class, () -> service.getLiteratureById(1L, 9L));
    }

    @Test
    void privateLiterature_readableByOwner() {
        when(literatureRepository.findById(1L)).thenReturn(Optional.of(lit(7L, false)));
        assertEquals("T", service.getLiteratureById(1L, 7L).getTitle());
    }

    @Test
    void publicLiterature_readableByEveryone() {
        when(literatureRepository.findById(1L)).thenReturn(Optional.of(lit(7L, true)));
        assertEquals("T", service.getLiteratureById(1L, 9L).getTitle());
    }

    @Test
    void legacyNullLiterature_readableByEveryone() {
        when(literatureRepository.findById(1L)).thenReturn(Optional.of(lit(7L, null)));
        assertEquals("T", service.getLiteratureById(1L, 9L).getTitle());
    }

    @Test
    void privateFileHiddenFromOthers_too() {
        when(literatureRepository.findById(1L)).thenReturn(Optional.of(lit(7L, false)));
        assertThrows(LiteratureNotFoundException.class, () -> service.openFile(1L, 9L));
    }

    @Test
    void categoryRequiredWhenContributing() {
        // 公开贡献：必须选合法板块
        assertThrows(com.example.literaturesearch.exception.BusinessException.class,
                () -> LiteratureService.normalizeCategory(null, true));
        assertThrows(com.example.literaturesearch.exception.BusinessException.class,
                () -> LiteratureService.normalizeCategory("physics", true));
        assertEquals("computer-science",
                LiteratureService.normalizeCategory("computer-science", true));
        // 私有文献：板块可选，非法值清空
        assertNull(LiteratureService.normalizeCategory("whatever", false));
        assertEquals("medicine", LiteratureService.normalizeCategory("medicine", false));
        assertNull(LiteratureService.normalizeCategory(null, false));
    }
}
