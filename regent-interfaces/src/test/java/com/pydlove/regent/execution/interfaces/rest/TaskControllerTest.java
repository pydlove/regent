package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.command.AddTaskAdjustmentCommand;
import com.pydlove.regent.execution.application.command.TaskAdjustmentService;
import com.pydlove.regent.execution.application.command.TaskCommandService;
import com.pydlove.regent.execution.application.query.TaskQueryService;
import com.pydlove.regent.execution.domain.model.TaskAdjustment;
import com.pydlove.regent.execution.domain.model.TaskAdjustmentId;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.kernel.TokenUsage;
import com.pydlove.regent.support.TestCurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

class TaskControllerTest {

    private RecordingTaskCommandService taskCommandService;
    private RecordingTaskQueryService taskQueryService;
    private RecordingTaskAdjustmentService taskAdjustmentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskCommandService = new RecordingTaskCommandService();
        taskQueryService = new RecordingTaskQueryService();
        taskAdjustmentService = new RecordingTaskAdjustmentService();
        mockMvc = MockMvcBuilders.standaloneSetup(new TaskController(
                        taskQueryService,
                        taskCommandService,
                        null,
                        null,
                        taskAdjustmentService,
                        null))
                .addInterceptors(TestCurrentUser.interceptor())
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

        mockMvc.perform(get("/api/tasks/token-usage").param("projectId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input").value(1200))
                .andExpect(jsonPath("$.cachedInput").value(300))
                .andExpect(jsonPath("$.output").value(500))
                .andExpect(jsonPath("$.total").value(2000));

        assertEquals(ProjectId.of(7L), taskQueryService.projectId);
    }

    @Test
    @DisplayName("执行中调整接口返回新建记录")
    void createsTaskAdjustment() throws Exception {
        mockMvc.perform(post("/api/tasks/{id}/adjustments", 76)
                        .contentType(APPLICATION_JSON)
                        .content("{\"content\":\"补一套边界测试\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.taskId").value(76))
                .andExpect(jsonPath("$.source").value("TEMPORARY"))
                .andExpect(jsonPath("$.content").value("补一套边界测试"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertEquals(TaskId.of(76), taskAdjustmentService.command.taskId());
        assertEquals("补一套边界测试", taskAdjustmentService.command.temporaryContent());
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
        private ProjectId projectId;

        private RecordingTaskQueryService() {
            super(null, null, ZoneOffset.UTC);
        }

        @Override
        public TokenUsage tokenUsage(ProjectId projectId) {
            this.projectId = projectId;
            return usage;
        }

        @Override
        public TokenUsage tokenUsage(ProjectId projectId, UserId ownerId) {
            return tokenUsage(projectId);
        }

        @Override
        public com.pydlove.regent.execution.domain.model.Task get(TaskId id, UserId ownerId) {
            return null;
        }
    }

    private static final class RecordingTaskAdjustmentService extends TaskAdjustmentService {

        private AddTaskAdjustmentCommand command;

        private RecordingTaskAdjustmentService() {
            super(null, null, null);
        }

        @Override
        public TaskAdjustment add(AddTaskAdjustmentCommand command) {
            this.command = command;
            TaskAdjustment adjustment = TaskAdjustment.temporary(command.taskId(), command.temporaryContent());
            adjustment.assignIdentity(TaskAdjustmentId.of(5L));
            return adjustment;
        }
    }
}
