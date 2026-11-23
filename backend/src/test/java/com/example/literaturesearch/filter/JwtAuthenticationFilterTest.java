package com.example.literaturesearch.filter;

import com.example.literaturesearch.util.JwtUtil;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            jwtUtil, mock(UserDetailsService.class));

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        // 伪造令牌在真实场景会触发 JwtException，mock 同样让它抛出
        when(jwtUtil.extractEmail(anyString())).thenThrow(new MalformedJwtException("bad token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void loginEndpoint_withGarbageToken_stillReachesController() throws Exception {
        // 死锁修复回归：带着过期/伪造令牌来登录，登录请求必须照常到达控制器
        // （过滤器放行后 MockFilterChain 不做任何事，响应保持 200；被拒绝则是 401）
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("Authorization", "Bearer expired.or.garbage.token");
        MockHttpServletResponse response = run(request);

        assertEquals(200, response.getStatus());
    }

    @Test
    void registerEndpoint_withGarbageToken_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.addHeader("Authorization", "Bearer garbage");
        assertEquals(200, run(request).getStatus());
    }

    @Test
    void captchaEndpoint_withoutToken_passesThrough() throws Exception {
        assertEquals(200, run(new MockHttpServletRequest("GET", "/api/auth/captcha")).getStatus());
    }

    @Test
    void meEndpoint_withGarbageToken_rejectedWith401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer garbage");
        MockHttpServletResponse response = run(request);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("401"));
    }

    @Test
    void protectedEndpoint_withGarbageToken_rejectedWith401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/literature");
        request.addHeader("Authorization", "Bearer garbage");
        MockHttpServletResponse response = run(request);

        assertEquals(401, response.getStatus());
    }

    @Test
    void protectedEndpoint_withoutToken_passesFilterChain() throws Exception {
        // 无令牌交给 Spring Security 的认证入口处理（403），过滤器不拦截
        MockHttpServletResponse response = run(new MockHttpServletRequest("GET", "/api/literature"));
        assertEquals(200, response.getStatus());
    }
}
