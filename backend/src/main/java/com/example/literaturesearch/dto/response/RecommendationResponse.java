package com.example.literaturesearch.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 社区推荐结果：basedOn 为参与计算的"我的文献"标题（前端据此说明推荐依据），
 * items 为按相似度降序的社区他人文献。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {

    private List<String> basedOn;
    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private LiteratureResponse literature;
        private Double similarity;
        private String snippet;
        /** 触发该推荐的我的文献标题 */
        private String basedOnTitle;
    }
}
