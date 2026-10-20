package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.command.AgentRunService;
import com.pydlove.regent.execution.application.query.AgentContractService;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.support.TestCurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeviceAgentControllerTest {

    private AgentRunService agentRunService;
    private TaskRepository taskRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        agentRunService = mock(AgentRunService.class);
        taskRepository = mock(TaskRepository.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DeviceAgentController(
                        agentRunService,
                        mock(AgentContractService.class),
                        taskRepository,
                        mock(TaskRunRepository.class),
                        mock(ProjectProfileRepository.class)))
                .addInterceptors(TestCurrentUser.interceptor())
                .build();
    }

    @Test
    @DisplayName("设备代理只能认领当前账号名下的指定任务")
    void claimsOwnedTask() throws Exception {
        Task task = Task.manual(ProjectId.of(3L), "设备任务", "在本机执行", 0);
        task.assignIdentity(TaskId.of(42L));
        when(taskRepository.findByIdOwned(TaskId.of(42L), UserId.of(TestCurrentUser.USER_ID)))
                .thenReturn(Optional.of(task));
        when(agentRunService.claim("device@test", "session-1", "regent", 42L))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/device-agent/tasks/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"executor":"device@test","sessionId":"session-1","projectCode":"regent","taskId":42}
                                """))
                .andExpect(status().isNoContent());

        verify(taskRepository).findByIdOwned(
                TaskId.of(42L), UserId.of(TestCurrentUser.USER_ID));
    }
}
