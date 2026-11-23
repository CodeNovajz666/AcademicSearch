package com.example.literaturesearch.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 笔记创建/更新请求。
 * 创建时 literatureId 必填；更新时可省略（不支持移动到其他文献）。
 * rects 为选区矩形数组（百分比坐标），badge 为角标位置，由后端序列化为 JSON 透明存储。
 */
@Data
public class NoteRequest {

    private Long literatureId;

    /** annotation / question / record，缺省 annotation */
    private String type;

    /** 锚定页码（question/record 可为空） */
    private Integer page;

    /** 锚点引文 / 用户选中的引用原文 */
    private String quote;

    /** 选区矩形 [{l,t,w,h}, ...]（页面百分比） */
    private List<Map<String, Object>> rects;

    /** 角标位置 {l,t}（页面百分比） */
    private Map<String, Object> badge;

    /** 引用标记：被标记的引文原文（如 "[12]"） */
    private String citeText;

    /** 色板 key：yellow/green/blue/pink */
    private String color;

    /** 笔记正文 */
    private String content;
}
