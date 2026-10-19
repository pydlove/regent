package com.pydlove.regent.bug.interfaces.rest;

import com.pydlove.regent.bug.application.command.BugCommandService;
import com.pydlove.regent.bug.application.query.BugQueryService;
import com.pydlove.regent.bug.domain.model.Bug;
import com.pydlove.regent.bug.domain.model.BugId;
import com.pydlove.regent.bug.domain.model.BugSeverity;
import com.pydlove.regent.bug.domain.model.BugSource;
import com.pydlove.regent.bug.domain.model.BugStatus;
import com.pydlove.regent.bug.domain.repository.BugView;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.support.TestCurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BugControllerTest {

    private RecordingBugCommandService commandService;
    private RecordingBugQueryService queryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        commandService = new RecordingBugCommandService();
        queryService = new RecordingBugQueryService();
        mockMvc = MockMvcBuilders.standaloneSetup(new BugController(
                        queryService, commandService, null, null))
                .addInterceptors(TestCurrentUser.interceptor())
                .build();
    }

    @Test
    @DisplayName("标记已修复走独立动作接口")
    void marksFixed() throws Exception {
        queryService.status = BugStatus.FIXED;

        mockMvc.perform(post("/api/bugs/{id}/mark-fixed", 108))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(108))
                .andExpect(jsonPath("$.status").value("FIXED"));

        assertEquals(BugId.of(108), commandService.markedFixed);
    }

    @Test
    @DisplayName("验证关闭走独立动作接口")
    void verifies() throws Exception {
        queryService.status = BugStatus.CLOSED;

        mockMvc.perform(post("/api/bugs/{id}/verify", 108))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertEquals(BugId.of(108), commandService.verified);
    }

    @Test
    @DisplayName("不修复接口把理由交给应用服务")
    void marksWontFix() throws Exception {
        queryService.status = BugStatus.WONTFIX;

        mockMvc.perform(post("/api/bugs/{id}/wont-fix", 108)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"上游接口决定保留该行为\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WONTFIX"));

        assertEquals(BugId.of(108), commandService.wontFixId);
        assertEquals("上游接口决定保留该行为", commandService.wontFixReason);
    }

    private static final class RecordingBugCommandService extends BugCommandService {

        private BugId markedFixed;
        private BugId verified;
        private BugId wontFixId;
        private String wontFixReason;

        private RecordingBugCommandService() {
            super(null, null);
        }

        @Override
        public Bug markFixed(BugId id) {
            markedFixed = id;
            return null;
        }

        @Override
        public Bug verify(BugId id) {
            verified = id;
            return null;
        }

        @Override
        public Bug wontFix(BugId id, String reason) {
            wontFixId = id;
            wontFixReason = reason;
            return null;
        }
    }

    private static final class RecordingBugQueryService extends BugQueryService {

        private BugStatus status = BugStatus.FIXED;

        private RecordingBugQueryService() {
            super(null);
        }

        @Override
        public BugView get(long id) {
            Instant now = Instant.parse("2026-10-17T00:00:00Z");
            return new BugView(
                    id, 1L, "Regent", null, null, 12L, BugSource.MANUAL,
                    "导出文件名乱码", BugSeverity.MAJOR, status,
                    null, null, null, now, null, null);
        }

        @Override
        public BugView get(long id, UserId ownerId) {
            return get(id);
        }
    }
}
