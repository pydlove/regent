package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.command.TaskCommandService;
import com.pydlove.regent.execution.application.query.TaskQueryService;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.shared.kernel.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerTest {

    private RecordingTaskCommandService taskCommandService;
    private RecordingTaskQueryService taskQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskCommandService = new RecordingTaskCommandService();
        taskQueryService = new RecordingTaskQueryService();
        mockMvc = MockMvcBuilders.standaloneSetup(new TaskController(
                        taskQueryService,
                        taskCommandService,
                        null,
                        null))
                .build();
    }

    @Test
    @DisplayName("删除任务走 DELETE 接口并返回 204")
    void deletesTaskThroughDeleteEndpoint() throws Exception {
        mockMvc.perform(delete("/api/tasks/{id}", 76))
                .andExpect(status().isNoContent());

        assertEquals(TaskId.of(76), taskCommandService.deleted);
    }

    @Test
    @DisplayName("Token 汇总接口返回输入、缓存、输出与总量")
    void returnsTokenUsageThroughGetEndpoint() throws Exception {
        taskQueryService.usage = TokenUsage.of(1200, 300, 500);

        mockMvc.perform(get("/api/tasks/token-usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input").value(1200))
                .andExpect(jsonPath("$.cachedInput").value(300))
                .andExpect(jsonPath("$.output").value(500))
                .andExpect(jsonPath("$.total").value(2000));
    }

    private static final class RecordingTaskCommandService extends TaskCommandService {

        private TaskId deleted;

        private RecordingTaskCommandService() {
            super(null, null);
        }

        @Override
        public void delete(TaskId id) {
            this.deleted = id;
        }
    }

    private static final class RecordingTaskQueryService extends TaskQueryService {

        private TokenUsage usage = TokenUsage.ZERO;

        private RecordingTaskQueryService() {
            super(null, null, ZoneOffset.UTC);
        }

        @Override
        public TokenUsage tokenUsage() {
            return usage;
        }
    }
}
