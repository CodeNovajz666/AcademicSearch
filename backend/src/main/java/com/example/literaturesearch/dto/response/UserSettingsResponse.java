package com.example.literaturesearch.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户级智能体设置。GET 与 PUT 共用该结构：
 * - GET：apiKey 只回掩码（如 sk-38****b767），完整 Key 不出服务端；hasApiKey 标识是否已保存。
 * - PUT：apiKey 留空或提交掩码值 = 不修改已保存的 Key；clearApiKey=true 删除已保存的 Key。
 * - chatModel / subagentModel 为可写字段，usingServerKey / hasApiKey 仅响应返回。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsResponse {

    private String apiKey;
    private String chatModel;
    private String subagentModel;
    /** true = 未配置个人 Key，智能体使用服务器默认密钥 */
    private boolean usingServerKey;
    /** true = 已保存个人 Key（GET 响应中 apiKey 为掩码） */
    private boolean hasApiKey;
    /** PUT 专用：删除已保存的个人 Key，回退服务器默认 */
    private Boolean clearApiKey;
}
