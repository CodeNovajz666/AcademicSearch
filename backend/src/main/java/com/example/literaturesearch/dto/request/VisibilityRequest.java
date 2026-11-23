package com.example.literaturesearch.dto.request;

import lombok.Data;

/** 可见性切换请求：true = 公开到社区总知识库，false = 设回私有。 */
@Data
public class VisibilityRequest {
    private Boolean isPublic;
}
