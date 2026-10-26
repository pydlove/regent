package com.pydlove.regent.identity.interfaces.rest;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.pydlove.regent.identity.application.command.LoginCommand;
import com.pydlove.regent.identity.application.command.MobileAuthService;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.application.query.MobileAuthGrant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MobileAuthControllerTest {

    private MobileAuthService service;
    private MockMvc mockMvc;
    private MobileAuthGrant grant;

    @BeforeEach
    void setUp() {
        service = mock(MobileAuthService.class);
        grant = new MobileAuthGrant(
                new AuthenticatedUser(7, "alice", "Alice", "alice@example.com", null),
                "mat_access",
                Instant.parse("2026-10-21T02:00:00Z"),
                "mrt_refresh",
                Instant.parse("2026-11-20T01:30:00Z"));
        mockMvc = MockMvcBuilders.standaloneSetup(new MobileAuthController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json()
                                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                .build()))
                .build();
    }

    @Test
    @DisplayName("手机登录返回独立双令牌与公开用户资料")
    void logsInWithMobileTokenPair() throws Exception {
        when(service.login(any(LoginCommand.class), eq("install-12345678")))
                .thenReturn(grant);

        mockMvc.perform(post("/api/mobile/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"secret","installationId":"install-12345678"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mat_access"))
                .andExpect(jsonPath("$.refreshToken").value("mrt_refresh"))
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    @DisplayName("刷新接口只接收刷新令牌，不依赖浏览器 Cookie")
    void refreshesTokenPair() throws Exception {
        when(service.refresh("mrt_refresh")).thenReturn(grant);

        mockMvc.perform(post("/api/mobile/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"mrt_refresh"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mat_access"))
                .andExpect(jsonPath("$.refreshToken").value("mrt_refresh"));
    }

    @Test
    @DisplayName("登出从 Bearer 头读取手机访问令牌")
    void logsOutWithBearerToken() throws Exception {
        mockMvc.perform(post("/api/mobile/auth/logout")
                        .header("Authorization", "Bearer mat_access"))
                .andExpect(status().isNoContent());

        verify(service).logout("mat_access");
    }
}
