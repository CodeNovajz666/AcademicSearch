package com.example.literaturesearch.service;

import com.example.literaturesearch.config.DashScopeConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class KnowledgeExtractionServiceTest {

    private final KnowledgeExtractionService service = new KnowledgeExtractionService(
            null, new DashScopeConfig(), new ObjectMapper());

    @Test
    void parse_plainJson_extractsAllFields() {
        String output = """
                {"main_content":"研究RAG幻觉问题","methods":"提出X方法，步骤为...","experiments":"在3个数据集上对比","conclusions":"X显著优于基线","title":"T","abstract":"A"}
                """;
        KnowledgeExtractionService.Knowledge k = service.parse(output);
        assertNotNull(k);
        assertEquals("研究RAG幻觉问题", k.mainContent());
        assertEquals("提出X方法，步骤为...", k.methods());
        assertEquals("在3个数据集上对比", k.experiments());
        assertEquals("X显著优于基线", k.conclusions());
        assertEquals("T", k.title());
        assertEquals("A", k.abstractText());
    }

    @Test
    void parse_fencedJsonWithProse_stillParses() {
        String output = """
                好的，以下是抽取结果：
                ```json
                {"main_content":"内容","methods":null,"experiments":"实验","conclusions":"结论"}
                ```
                以上。
                """;
        KnowledgeExtractionService.Knowledge k = service.parse(output);
        assertNotNull(k);
        assertEquals("内容", k.mainContent());
        assertNull(k.methods());
        assertEquals("实验", k.experiments());
        assertEquals("结论", k.conclusions());
        assertNull(k.title());
    }

    @Test
    void parse_stringNullAndMissing_treatedAsNull() {
        String output = "{\"main_content\":\"null\",\"methods\":\"\"}";
        KnowledgeExtractionService.Knowledge k = service.parse(output);
        assertNotNull(k);
        assertNull(k.mainContent());
        assertNull(k.methods());
    }

    @Test
    void parse_noJson_returnsNull() {
        assertNull(service.parse("抱歉，我无法解析该文档"));
        assertNull(service.parse(""));
        assertNull(service.parse(null));
        assertNull(service.parse("{ broken"));
    }

    private static final String GOOD_JSON =
            "{\"main_content\":\"内容\",\"methods\":\"m\",\"experiments\":\"e\",\"conclusions\":\"c\"}";

    @Test
    void extract_unparseableOutput_retriesAndSucceeds() {
        DashScopeService dash = Mockito.mock(DashScopeService.class);
        when(dash.chatRaw(any(), anyString(), anyString(), eq(4096)))
                .thenReturn("{ broken")           // 第一次：输出被截断
                .thenReturn(GOOD_JSON);           // 重试成功
        KnowledgeExtractionService svc = new KnowledgeExtractionService(
                dash, new DashScopeConfig(), new ObjectMapper());

        KnowledgeExtractionService.Knowledge k = svc.extract("Some paper text");
        assertNotNull(k);
        assertEquals("内容", k.mainContent());
        Mockito.verify(dash, Mockito.times(2)).chatRaw(any(), anyString(), anyString(), eq(4096));
    }

    @Test
    void extract_allNullKnowledgeFields_retries() {
        DashScopeService dash = Mockito.mock(DashScopeService.class);
        when(dash.chatRaw(any(), anyString(), anyString(), eq(4096)))
                .thenReturn("{\"main_content\":null,\"methods\":null,\"experiments\":null,\"conclusions\":null}")
                .thenReturn(GOOD_JSON);
        KnowledgeExtractionService svc = new KnowledgeExtractionService(
                dash, new DashScopeConfig(), new ObjectMapper());

        KnowledgeExtractionService.Knowledge k = svc.extract("Some paper text");
        assertNotNull(k);
        assertEquals("内容", k.mainContent());
    }

    @Test
    void extract_bothAttemptsFail_returnsNull() {
        DashScopeService dash = Mockito.mock(DashScopeService.class);
        when(dash.chatRaw(any(), anyString(), anyString(), eq(4096)))
                .thenReturn("not json at all");
        KnowledgeExtractionService svc = new KnowledgeExtractionService(
                dash, new DashScopeConfig(), new ObjectMapper());

        assertNull(svc.extract("Some paper text"));
        Mockito.verify(dash, Mockito.times(2)).chatRaw(any(), anyString(), anyString(), eq(4096));
    }

    @Test
    void extract_apiError_returnsNullWithoutRetryOnSameAttempt() {
        DashScopeService dash = Mockito.mock(DashScopeService.class);
        when(dash.chatRaw(any(), anyString(), anyString(), eq(4096)))
                .thenThrow(new RuntimeException("DashScope API 500"));
        KnowledgeExtractionService svc = new KnowledgeExtractionService(
                dash, new DashScopeConfig(), new ObjectMapper());

        assertNull(svc.extract("Some paper text"));
        Mockito.verify(dash, Mockito.times(2)).chatRaw(any(), anyString(), anyString(), eq(4096));
    }
}
