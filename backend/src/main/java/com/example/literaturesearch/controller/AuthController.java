package com.example.literaturesearch.controller;

import com.example.literaturesearch.dto.request.LoginRequest;
import com.example.literaturesearch.dto.request.RegisterRequest;
import com.example.literaturesearch.dto.request.RefreshRequest;
import com.example.literaturesearch.dto.response.ApiResponse;
import com.example.literaturesearch.dto.response.CaptchaResponse;
import com.example.literaturesearch.dto.response.LoginResponse;
import com.example.literaturesearch.dto.response.UserResponse;
import com.example.literaturesearch.service.AuthService;
import com.example.literaturesearch.service.CaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    @GetMapping("/captcha")
    public ResponseEntity<ApiResponse<CaptchaResponse>> captcha() {
        return ResponseEntity.ok(ApiResponse.success("获取验证码成功", captchaService.generate()));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        captchaService.validate(request.getCaptchaId(), request.getCaptchaCode());
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("注册成功", user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResponse loginResponse = authService.login(request, clientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("登录成功", loginResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        LoginResponse loginResponse = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("令牌刷新成功", loginResponse));
    }

    /**
     * 登出：吊销 refresh 令牌（服务端止损），请求体可选——
     * 无令牌/令牌无效时同样返回成功（幂等），前端随后自行清理本地状态。
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) RefreshRequest request) {
        authService.logout(request != null ? request.getRefreshToken() : null);
        return ResponseEntity.ok(ApiResponse.success("已退出登录", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        UserResponse user = authService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("获取用户信息成功", user));
    }

    /**
     * 取登录来源 IP。X-Forwarded-For 取第一个值（直接客户端）；
     * 注意该头可被伪造，IP 维度只是邮箱限流之外的粗粒度约束，不能作为唯一防线。
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            return first.trim();
        }
        return request.getRemoteAddr();
    }
}
