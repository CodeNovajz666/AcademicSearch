package com.example.literaturesearch.service;

import com.example.literaturesearch.dto.request.LoginRequest;
import com.example.literaturesearch.dto.request.RegisterRequest;
import com.example.literaturesearch.dto.response.LoginResponse;
import com.example.literaturesearch.dto.response.UserResponse;
import com.example.literaturesearch.entity.RevokedToken;
import com.example.literaturesearch.entity.User;
import com.example.literaturesearch.exception.BusinessException;
import com.example.literaturesearch.exception.UnauthorizedException;
import com.example.literaturesearch.repository.RevokedTokenRepository;
import com.example.literaturesearch.repository.UserRepository;
import com.example.literaturesearch.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final LoginRateLimiter loginRateLimiter;
    private final RevokedTokenRepository revokedTokenRepository;

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("该邮箱已被注册");
        }

        User user = User.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        User savedUser = userRepository.save(user);
        return UserResponse.fromEntity(savedUser);
    }

    public LoginResponse login(LoginRequest request, String clientIp) {
        loginRateLimiter.assertNotLocked(request.getEmail(), clientIp);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
            loginRateLimiter.reset(request.getEmail());

            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new BusinessException("用户不存在"));

            String accessToken = jwtUtil.generateToken(user);
            String refreshToken = jwtUtil.generateRefreshToken(user);

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .user(UserResponse.fromEntity(user))
                    .build();
        } catch (org.springframework.security.core.AuthenticationException e) {
            loginRateLimiter.recordFailure(request.getEmail(), clientIp);
            throw e;
        }
    }

    public LoginResponse refreshToken(String refreshToken) {
        try {
            String email = jwtUtil.extractEmail(refreshToken);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new BusinessException("用户不存在"));

            if (!jwtUtil.validateToken(refreshToken, user)) {
                throw new BusinessException("无效的刷新令牌");
            }
            // access 令牌不能当刷新令牌用（历史 refresh 无 type claim，仍接受以兼容存量登录态）
            if (jwtUtil.TYPE_ACCESS.equals(jwtUtil.extractType(refreshToken))) {
                throw new BusinessException("无效的刷新令牌");
            }
            // 登出已吊销的令牌不能再换新
            String jti = jwtUtil.extractJti(refreshToken);
            if (jti != null && revokedTokenRepository.existsById(jti)) {
                throw new BusinessException("登录状态已失效，请重新登录");
            }

            String newAccessToken = jwtUtil.generateToken(user);
            String newRefreshToken = jwtUtil.generateRefreshToken(user);

            return LoginResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .user(UserResponse.fromEntity(user))
                    .build();
        } catch (Exception e) {
            throw new BusinessException("刷新令牌无效或已过期");
        }
    }

    /**
     * 登出：吊销 refresh 令牌（jti 黑名单），被盗令牌在登出后立即失效。
     * 幂等：无令牌/无效令牌/历史无 jti 令牌均静默通过。
     */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            String jti = jwtUtil.extractJti(refreshToken);
            Date expiration = jwtUtil.extractExpiration(refreshToken);
            if (jti == null || expiration == null || !expiration.after(new Date())) {
                return; // 历史令牌无 jti 或已过期：无需吊销
            }
            revokedTokenRepository.save(RevokedToken.builder()
                    .jti(jti)
                    .expiresAt(expiration.toInstant())
                    .createdAt(Instant.now())
                    .build());
            // 顺带清理已过期的吊销记录，黑名单只覆盖未过期令牌
            revokedTokenRepository.deleteByExpiresAtBefore(Instant.now());
        } catch (JwtException | IllegalArgumentException e) {
            // 无效令牌：登出幂等
        }
    }

    public UserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("用户未登录");
        }

        Object principal = authentication.getPrincipal();
        // 过滤器注入的 principal 即 User 实体；getUsername() 是注册用户名而非邮箱，不能用于 findByEmail
        if (principal instanceof User user) {
            return UserResponse.fromEntity(user);
        }
        if (principal instanceof UserDetails userDetails) {
            User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
            if (user != null) {
                return UserResponse.fromEntity(user);
            }
        }
        // 匿名请求（无 Authorization 头）：principal 是 "anonymousUser" 字符串，规范返回 401
        throw new UnauthorizedException("用户未登录");
    }
}
