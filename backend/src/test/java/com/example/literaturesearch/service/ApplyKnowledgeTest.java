package com.example.literaturesearch.service;

import com.example.literaturesearch.entity.Literature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApplyKnowledgeTest {

    private Literature lit(String title, String abstractText) {
        return Literature.builder().id(1L).title(title).abstractText(abstractText).build();
    }

    @Test
    void bannerTitle_replacedByLlmTitle() {
        Literature l = lit("Published as a conference paper at ICLR 2026", null);
        KnowledgeExtractionService.Knowledge k = new KnowledgeExtractionService.Knowledge(
                "Activation Reasoning: Injecting Logic into Latent Space", "abs",
                "main", "methods", "exp", "concl");
        LiteratureProcessingService.applyKnowledge(l, k);
        assertEquals("Activation Reasoning: Injecting Logic into Latent Space", l.getTitle());
    }

    @Test
    void realTitle_neverOverwritten() {
        Literature l = lit("Attention Is All You Need", "有摘要");
        KnowledgeExtractionService.Knowledge k = new KnowledgeExtractionService.Knowledge(
                "另一个标题", "LLM摘要", "main", null, null, null);
        LiteratureProcessingService.applyKnowledge(l, k);
        assertEquals("Attention Is All You Need", l.getTitle());
        // 已有摘要也不覆盖
        assertEquals("有摘要", l.getAbstractText());
    }

    @Test
    void missingAbstract_filledFromLlm() {
        Literature l = lit("Real Title", null);
        KnowledgeExtractionService.Knowledge k = new KnowledgeExtractionService.Knowledge(
                null, "LLM 抽取的摘要", null, null, null, null);
        LiteratureProcessingService.applyKnowledge(l, k);
        assertEquals("LLM 抽取的摘要", l.getAbstractText());
    }

    @Test
    void bannerDetection_patterns() {
        assertTrue(LiteratureProcessingService.isBannerLike("Published as a conference paper at ICLR 2026"));
        assertTrue(LiteratureProcessingService.isBannerLike("Accepted by NeurIPS 2025"));
        assertTrue(LiteratureProcessingService.isBannerLike("Under review as a conference paper"));
        assertTrue(LiteratureProcessingService.isBannerLike("Proceedings of the 41st ICML"));
        assertFalse(LiteratureProcessingService.isBannerLike("Attention Is All You Need"));
        assertFalse(LiteratureProcessingService.isBannerLike(null));
    }

    @Test
    void nullKnowledge_noop() {
        Literature l = lit("T", "A");
        LiteratureProcessingService.applyKnowledge(l, null);
        assertEquals("T", l.getTitle());
        assertEquals("A", l.getAbstractText());
        assertNull(l.getMainContent());
    }
}
