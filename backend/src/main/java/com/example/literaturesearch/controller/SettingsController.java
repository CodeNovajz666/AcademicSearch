package com.example.literaturesearch.controller;

import com.example.literaturesearch.dto.response.ApiResponse;
import com.example.literaturesearch.dto.response.UserSettingsResponse;
import com.example.literaturesearch.entity.User;
import com.example.literaturesearch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户级智能体设置：个人 DashScope API Key 与模型选择。
 * 保存后由 Python Agent 读取并对该账号生效；Key 留空则回退服务器默认密钥。
 * 完整 Key 只进不出：GET 仅返回掩码，前端无法回读明文，降低 XSS/会话劫持盗刷面。
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private static final int MAX_API_KEY_LENGTH = 512;
    private static final int MAX_MODEL_LENGTH = 64;

    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<UserSettingsResponse>> getSettings(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(toResponse(user)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserSettingsResponse>> updateSettings(
            @AuthenticationPrincipal User user,
            @RequestBody UserSettingsResponse request) {

        // 语义：clearApiKey=true 删除已保存 Key；apiKey 留空或与掩码一致 = 不修改；
        // 只有提交了新的完整 Key 才更新
        if (Boolean.TRUE.equals(request.getClearApiKey())) {
            user.setDashscopeApiKey(null);
        } else {
            String apiKey = trimToNull(request.getApiKey());
            if (apiKey != null && !apiKey.equals(mask(user.getDashscopeApiKey()))) {
                if (apiKey.length() > MAX_API_KEY_LENGTH) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error(400, "API Key 过长（最多 " + MAX_API_KEY_LENGTH + " 个字符）"));
                }
                user.setDashscopeApiKey(apiKey);
            }
        }

        String chatModel = trimToNull(request.getChatModel());
        String subagentModel = trimToNull(request.getSubagentModel());
        if ((chatModel != null && chatModel.length() > MAX_MODEL_LENGTH)
                || (subagentModel != null && subagentModel.length() > MAX_MODEL_LENGTH)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "模型名称过长（最多 " + MAX_MODEL_LENGTH + " 个字符）"));
        }

        user.setChatModel(chatModel);
        user.setSubagentModel(subagentModel);
        userRepository.save(user);

        return ResponseEntity.ok(ApiResponse.success("设置已保存", toResponse(user)));
    }

    private static UserSettingsResponse toResponse(User user) {
        String apiKey = user.getDashscopeApiKey();
        boolean hasKey = apiKey != null && !apiKey.isBlank();
        return UserSettingsResponse.builder()
                .apiKey(hasKey ? mask(apiKey) : null)
                .chatModel(user.getChatModel())
                .subagentModel(user.getSubagentModel())
                .usingServerKey(!hasKey)
                .hasApiKey(hasKey)
                .build();
    }

    /** 掩码：保留前 4 后 4 位（过短则全掩码），前端展示与"未修改"判定都用该形态。 */
    private static String mask(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
