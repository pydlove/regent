package com.pydlove.regent.settings.interfaces.rest;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.pydlove.regent.settings.application.command.RelaySettingsService;
import com.pydlove.regent.settings.application.query.RelaySettingsView;
import com.pydlove.regent.settings.application.query.RelayState;
import com.pydlove.regent.settings.application.query.RelayStatusView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 中继设置接口的前端契约。 */
class RelaySettingsControllerTest {

    private static final Instant CHECKED_AT = Instant.parse("2026-10-17T02:30:00Z");

    private RecordingRelaySettingsService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = new RecordingRelaySettingsService();
        mockMvc = MockMvcBuilders.standaloneSetup(new RelaySettingsController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json()
                                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                .build()))
                .build();
    }

    @Test
    void 读取中继设置时返回服务器与连接器状态() throws Exception {
        mockMvc.perform(get("/api/settings/relay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverUrl").value("https://relay.example.com"))
                .andExpect(jsonPath("$.monitoringEnabled").value(true))
                .andExpect(jsonPath("$.status.state").value("DEGRADED"))
                .andExpect(jsonPath("$.status.connectorOnline").value(false))
                .andExpect(jsonPath("$.status.queueDepth").value(2))
                .andExpect(jsonPath("$.status.checkedAt").value("2026-10-17T02:30:00Z"));
    }

    @Test
    void 保存中继设置时把地址与开关传给应用服务() throws Exception {
        mockMvc.perform(put("/api/settings/relay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serverUrl":"https://new-relay.example.com","monitoringEnabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.state").value("DEGRADED"));

        assertEquals("https://new-relay.example.com", service.serverUrl);
        assertTrue(service.monitoringEnabled);
    }

    private static final class RecordingRelaySettingsService extends RelaySettingsService {

        private String serverUrl;
        private boolean monitoringEnabled;

        private RecordingRelaySettingsService() {
            super(null, null, null, Clock.systemUTC());
        }

        @Override
        public RelaySettingsView current() {
            return view("https://relay.example.com", true);
        }

        @Override
        public RelaySettingsView update(String serverUrl, boolean monitoringEnabled) {
            this.serverUrl = serverUrl;
            this.monitoringEnabled = monitoringEnabled;
            return view(serverUrl, monitoringEnabled);
        }

        private RelaySettingsView view(String url, boolean enabled) {
            return new RelaySettingsView(
                    url,
                    enabled,
                    new RelayStatusView(
                            RelayState.DEGRADED,
                            false,
                            2,
                            35L,
                            CHECKED_AT,
                            "连接器离线"
                    )
            );
        }
    }
}
