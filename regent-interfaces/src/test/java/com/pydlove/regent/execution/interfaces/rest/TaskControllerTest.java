package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.command.TaskCommandService;
import com.pydlove.regent.execution.domain.model.TaskId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerTest {

    private RecordingTaskCommandService taskCommandService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskCommandService = new RecordingTaskCommandService();
        mockMvc = MockMvcBuilders.standaloneSetup(new TaskController(
                        null,
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
}
