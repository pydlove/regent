package com.pydlove.regent.boot.config;

import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 管理端 API 的登录守卫。
 *
 * <p>外部执行器协议与邮件审批令牌是两套独立的机器入口，不能拿浏览器 Cookie 去卡它们；
 * 除此之外的 /api 接口都要求会话。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthSessionInterceptor authSessionInterceptor;

    public WebConfig(AuthSessionInterceptor authSessionInterceptor) {
        this.authSessionInterceptor = authSessionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authSessionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/email-codes",
                        "/api/auth/login",
                        "/api/auth/me",
                        "/api/auth/logout",
                        "/api/agent/**",
                        "/api/approvals/**");
    }
}
