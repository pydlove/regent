package com.pydlove.regent.identity.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.identity.application.command.AuthService;
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

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public AuthSessionInterceptor(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        if (authService.authenticate(readToken(request)).isPresent()) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiError.of("UNAUTHORIZED", "请先登录"));
        return false;
    }

    static String readToken(HttpServletRequest request) {
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
}
