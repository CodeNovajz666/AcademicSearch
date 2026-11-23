package com.example.literaturesearch.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiteratureServiceTest {

<<<<<<< HEAD
    private final LiteratureService literatureService = new LiteratureService(null, null, null, null, null);
=======
    private final LiteratureService literatureService = new LiteratureService(null, null);
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

    @Test
    void extractAbstract_nullOrEmpty_returnsNull() {
        assertNull(literatureService.extractAbstract(null));
        assertNull(literatureService.extractAbstract(""));
        assertNull(literatureService.extractAbstract("正文里没有任何标记的内容"));
    }

    @Test
    void extractAbstract_chineseMarker_extractsFullText() {
        String content = "论文标题\n摘要：本文提出了一种新的检索方法。Keywords: 检索\n后续章节内容";
        assertEquals("本文提出了一种新的检索方法。", literatureService.extractAbstract(content));
    }

    @Test
    void extractAbstract_englishMarker_stopsAtKeywords() {
        String content = "Paper Title\nAbstract—We propose a novel method. Keywords: retrieval, embedding";
        assertEquals("We propose a novel method.", literatureService.extractAbstract(content));
    }

    @Test
    void extractAbstract_englishMarker_lowercase() {
        String content = "Paper Title\nabstract: Vector search is useful. introduction follows";
        assertEquals("Vector search is useful.", literatureService.extractAbstract(content));
    }

    @Test
    void extractAbstract_stopsAtIntroductionWhenNoKeywords() {
        String content = "Title\nAbstract Deep learning models improve retrieval.\nIntroduction\nIn this paper";
        assertEquals("Deep learning models improve retrieval.", literatureService.extractAbstract(content));
    }
<<<<<<< HEAD

    @Test
    void extractAbstract_stripsTrailingSectionNumber() {
        // "1 Introduction" 的节号残留在摘要末尾，视觉上像被截断，应剥离
        String content = "Title\nAbstract Our findings hold in various environments.\n1 Introduction\nWe study...";
        assertEquals("Our findings hold in various environments.", literatureService.extractAbstract(content));
    }

    @Test
    void extractAbstract_realAbstractEndingInNumber_notStripped() {
        // 摘要末尾本身以数字结束（超过 3 位或无句末标点）不受剥离影响
        String content = "Title\nAbstract The dataset contains 2023 samples for evaluation.";
        assertEquals("The dataset contains 2023 samples for evaluation.", literatureService.extractAbstract(content));
    }

    @Test
    void extractAbstract_longText_notTruncated() {
        // 已取消字数限制：无 keywords/introduction 标记的长文本也完整保留
        String content = "Title\nAbstract " + "x".repeat(9000);
        assertEquals(9000, literatureService.extractAbstract(content).length());
    }
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
}
