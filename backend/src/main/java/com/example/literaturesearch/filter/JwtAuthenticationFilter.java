package com.example.literaturesearch.filter;

import com.example.literaturesearch.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        // 公共认证端点完全跳过 JWT 处理：用户带着"过期令牌"来重新登录时，
        // 登录请求自身也携带旧 Authorization 头，若在此处解析失败直接拒绝，
        // 登录将永远无法到达控制器（死锁在"登录已过期"提示上）。
        // /api/auth/me 需要令牌换取当前用户，不在此列。
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/") && !path.equals("/api/auth/me")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);
        String userEmail;
        try {
            userEmail = jwtUtil.extractEmail(jwt);
            // refresh 令牌只用于 /api/auth/refresh 换新，不能直接当访问令牌用
            if (com.example.literaturesearch.util.JwtUtil.TYPE_REFRESH.equals(jwtUtil.extractType(jwt))) {
                writeUnauthorized(response, "无效的访问令牌");
                return;
            }
        } catch (JwtException | IllegalArgumentException e) {
            // 无效/过期令牌必须规范返回 401：异常外泄会变成无 CORS 头的 500 错误页，
            // 浏览器将其视为网络错误（Failed to fetch），前端也就没有机会用 refresh_token 续期
            writeUnauthorized(response, "登录已过期，请重新登录");
            return;
        }

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails;
            try {
                userDetails = this.userDetailsService.loadUserByUsername(userEmail);
            } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
                writeUnauthorized(response, "登录已过期，请重新登录");
                return;
            }

            if (jwtUtil.validateToken(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    private static void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}");
    }
}
