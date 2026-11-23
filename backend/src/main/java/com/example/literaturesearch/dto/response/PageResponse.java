package com.example.literaturesearch.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页响应：列表接口统一返回 { items, total, page, size }。
 * page 从 0 计（Spring Data 惯例），前端传页码递增加载。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> items;
    private long total;
    private int page;
    private int size;

    public static <T> PageResponse<T> of(List<T> items, long total, int page, int size) {
        return PageResponse.<T>builder().items(items).total(total).page(page).size(size).build();
    }

    public static <T> PageResponse<T> empty(int page, int size) {
        return of(List.of(), 0, page, size);
    }
}
