package com.pydlove.regent.identity.interfaces.rest;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthSessionInterceptorTest {

    @Test
    @DisplayName("设备代理可通过 Bearer 令牌认证")
    void readsBearerToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer device-token");

        assertEquals("device-token", AuthSessionInterceptor.readToken(request));
    }

    @Test
    @DisplayName("浏览器仍使用原有 HttpOnly Cookie")
    void readsCookieToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthSessionInterceptor.COOKIE_NAME, "cookie-token"));

        assertEquals("cookie-token", AuthSessionInterceptor.readToken(request));
    }
}
