package com.pydlove.regent.support;

import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/** 接口层单测只需要一个稳定的登录身份，真实会话校验由 AuthSessionInterceptor 自己覆盖。 */
public final class TestCurrentUser {

    public static final long USER_ID = 1L;

    private TestCurrentUser() {
    }

    public static HandlerInterceptor interceptor() {
        return interceptor(USER_ID);
    }

    public static HandlerInterceptor interceptor(long userId) {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request,
                                     HttpServletResponse response,
                                     Object handler) {
                request.setAttribute(AuthSessionInterceptor.CURRENT_USER_ATTRIBUTE,
                        new AuthenticatedUser(userId, "tester", "测试账号", "tester@example.com", null));
                return true;
            }
        };
    }
}
