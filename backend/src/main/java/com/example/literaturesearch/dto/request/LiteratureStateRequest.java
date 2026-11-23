package com.example.literaturesearch.dto.request;

import lombok.Data;

/** 文献私有状态更新：三个字段均可选，只更新传入的字段（partial update）。 */
@Data
public class LiteratureStateRequest {
    private Boolean starred;
    private Boolean read;
    /** 传空串清除笔记 */
    private String note;
}
