package com.pydlove.regent.boot.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobileCorsIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Capacitor 固定来源可预检 Bearer 手机请求")
    void acceptsCapacitorPreflight() throws Exception {
        mockMvc.perform(options("/api/projects")
                        .header(HttpHeaders.ORIGIN, "capacitor://localhost")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "Authorization, X-Regent-Relay-Request"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "capacitor://localhost"));
    }

    @Test
    @DisplayName("任意第三方网页来源不能加入手机 API 白名单")
    void rejectsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/projects")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }
}
