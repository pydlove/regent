package com.pydlove.regent.identity.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.identity.application.command.AuthService;
import com.pydlove.regent.identity.application.command.MobileAuthService;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.interfaces.dto.ApiError;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 除公开入口与机器执行器协议外，管理 API 一律要求有效登录会话。 */
@Component
public class AuthSessionInterceptor implements HandlerInterceptor {

    static final String COOKIE_NAME = "regent_session";
    public static final String CURRENT_USER_ATTRIBUTE =
            AuthSessionInterceptor.class.getName() + ".currentUser";

    private final AuthService authService;
    private final MobileAuthService mobileAuthService;
    private final ObjectMapper objectMapper;

    public AuthSessionInterceptor(AuthService authService,
                                  MobileAuthService mobileAuthService,
                                  ObjectMapper objectMapper) {
        this.authService = authService;
        this.mobileAuthService = mobileAuthService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                && request.getHeader("Origin") != null
                && request.getHeader("Access-Control-Request-Method") != null) {
            // 预检本身没有 Bearer 令牌；来源是否合法交给 Spring 的 CORS 白名单判断。
            return true;
        }
        String token = readToken(request);
        AuthenticatedUser user = mobileAuthService.authenticate(token)
                .or(() -> authService.authenticate(token))
                .orElse(null);
        if (user != null) {
            if (user.mustChangePassword() && !passwordChangeAllowed(request)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getWriter(),
                        ApiError.of("PASSWORD_CHANGE_REQUIRED", "请先修改初始密码"));
                return false;
            }
            request.setAttribute(CURRENT_USER_ATTRIBUTE, user);
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiError.of("UNAUTHORIZED", "请先登录"));
        return false;
    }

    public static String readToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String bearer = authorization.substring(7).trim();
            if (!bearer.isEmpty()) return bearer;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public static AuthenticatedUser currentUser(HttpServletRequest request) {
        Object value = request.getAttribute(CURRENT_USER_ATTRIBUTE);
        if (value instanceof AuthenticatedUser user) {
            return user;
        }
        throw new IllegalStateException("登录守卫没有提供当前用户");
    }

    private static boolean passwordChangeAllowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api/auth/me".equals(path)
                || "/api/auth/logout".equals(path)
                || "/api/auth/change-password".equals(path);
    }
}
