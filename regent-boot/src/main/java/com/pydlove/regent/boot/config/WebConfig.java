package com.pydlove.regent.boot.config;

import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
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

    private static final String[] MOBILE_APP_ORIGINS = {
            "capacitor://localhost",
            "https://localhost",
            // Vite 开发服务器把请求代理到 8787 时会保留浏览器的 Origin。
            // 这是本机开发来源；生产同源请求由中继统一去掉 Origin。
            "http://127.0.0.1:12580",
            "http://localhost:12580"
    };

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
                        "/api/invitations/**",
                        "/api/mobile/auth/login",
                        "/api/mobile/auth/refresh",
                        "/api/release/**",
                        "/api/agent/**",
                        "/api/approvals/**");
    }

    /**
     * iOS 与 Android WebView 的固定本地来源不同，Bearer 请求又是跨域请求。
     * 只放行 Capacitor 固定来源，不使用通配符，也不允许携带 Cookie。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(MOBILE_APP_ORIGINS)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept",
                        "X-Regent-Relay-Request", "X-Organization-Id")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
