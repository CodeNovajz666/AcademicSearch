package com.example.literaturesearch.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VectorServiceTest {

    private final VectorService vectorService = new VectorService(null, null, null);

    @Test
    void splitTextIntoChunks_nullOrBlank_returnsEmpty() {
        assertTrue(vectorService.splitTextIntoChunks(null, 500, 100).isEmpty());
        assertTrue(vectorService.splitTextIntoChunks("", 500, 100).isEmpty());
        assertTrue(vectorService.splitTextIntoChunks("   ", 500, 100).isEmpty());
    }

    @Test
    void splitTextIntoChunks_shortText_singleChunk() {
        List<String> chunks = vectorService.splitTextIntoChunks("很短的文本", 500, 100);
        assertEquals(1, chunks.size());
        assertEquals("很短的文本", chunks.get(0));
    }

    @Test
    void splitTextIntoChunks_chineseText_prefersSentenceBoundary() {
        String text = "这是第一句话的内容。这是第二句话的内容！这是第三句话的内容？".repeat(20);
        List<String> chunks = vectorService.splitTextIntoChunks(text, 500, 100);

        assertFalse(chunks.isEmpty());
        // 除最后一块外，其余块应在句子边界处结束（在可选边界存在时）
        assertTrue(chunks.get(0).endsWith("。") || chunks.get(0).endsWith("！") || chunks.get(0).endsWith("？"));
        chunks.forEach(c -> assertTrue(c.length() <= 500));
        // 所有分块拼接应能覆盖全文（存在重叠）
        String joined = String.join("", chunks);
        assertTrue(joined.contains("这是第一句话的内容"));
        assertTrue(joined.contains("这是第三句话的内容？"));
    }

    @Test
    void splitTextIntoChunks_englishText_cutsAtPeriod() {
        String sentence = "This sentence has exactly enough words to fill space. ";
        String text = sentence.repeat(30);
        List<String> chunks = vectorService.splitTextIntoChunks(text, 500, 100);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).endsWith("."));
        chunks.forEach(c -> assertTrue(c.length() <= 500));
    }

    @Test
    void splitTextIntoChunks_textWithoutPunctuation_terminatesWithFixedChunks() {
        String text = "字".repeat(5000);
        List<String> chunks = vectorService.splitTextIntoChunks(text, 500, 100);

        assertEquals(13, chunks.size());
        chunks.forEach(c -> assertTrue(c.length() <= 500));
        // 无标点时不做边界调整，每块均为整块
        assertEquals(500, chunks.get(0).length());
    }
}
