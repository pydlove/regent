package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.command.AgentRunService;
import com.pydlove.regent.execution.application.command.DeviceHeartbeat;
import com.pydlove.regent.execution.application.command.DevicePresence;
import com.pydlove.regent.execution.application.command.DevicePresenceService;
import com.pydlove.regent.execution.application.query.AgentContractService;
import com.pydlove.regent.execution.domain.model.ExecutionDevice;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.execution.interfaces.dto.DevicePresenceRequest;
import com.pydlove.regent.execution.interfaces.dto.DevicePresenceResponse;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeviceAgentControllerTest {

    private AgentRunService agentRunService;
    private TaskRepository taskRepository;
    private TaskRunRepository taskRunRepository;
    private DevicePresenceService presenceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        agentRunService = mock(AgentRunService.class);
        taskRepository = mock(TaskRepository.class);
        taskRunRepository = mock(TaskRunRepository.class);
        presenceService = mock(DevicePresenceService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DeviceAgentController(
                        agentRunService,
                        mock(AgentContractService.class),
                        taskRepository,
                        taskRunRepository,
                        mock(ProjectProfileRepository.class),
                        presenceService))
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
        when(agentRunService.claim("device@test", "session-1", "regent", 42L, null))
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

    @Test
    @DisplayName("设备心跳会把当前账号的持久化排队任务重新投递，且一次只投一条")
    void resumesPersistentQueuedTaskOnPresence() throws Exception {
        UserId owner = UserId.of(TestCurrentUser.USER_ID);
        DevicePresenceRequest request = new DevicePresenceRequest(
                "device-1", "macbook", "darwin", "device@test",
                new DevicePresenceRequest.Codex(
                        true, "/tmp/codex", "codex-cli 1.0", "可用", "2026-10-20T04:00:00Z"));
        Instant seenAt = Instant.parse("2026-10-20T04:00:00Z");
        ExecutionDevice device = ExecutionDevice.heartbeat(
                null, owner, "device-1", "macbook", "darwin", "device@test", "instance-1",
                new ExecutionDevice.CodexStatus(
                        true, "/tmp/codex", "codex-cli 1.0", "可用", seenAt),
                "R100B002", "R100B002", seenAt);
        when(presenceService.heartbeat(eq(owner), any(DeviceHeartbeat.class)))
                .thenReturn(new DevicePresence(device, true, List.of(42L)));

        mockMvc.perform(post("/api/device-agent/presence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "device-1",
                                  "hostname": "macbook",
                                  "platform": "darwin",
                                  "executor": "device@test",
                                  "codex": {
                                    "available": true,
                                    "executablePath": "/tmp/codex",
                                    "version": "codex-cli 1.0",
                                    "message": "可用",
                                    "checkedAt": "2026-10-20T04:00:00Z"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskIds[0]").value(42));

        verify(presenceService).heartbeat(eq(owner), any(DeviceHeartbeat.class));
    }

    @Test
    @DisplayName("设备代理可以为自己的运行记录追加实时事件")
    void appendsRunEvents() throws Exception {
        Task task = Task.manual(ProjectId.of(3L), "设备任务", "在本机执行", 0);
        task.assignIdentity(TaskId.of(42L));
        TaskRun run = mock(TaskRun.class);
        when(run.getTaskId()).thenReturn(TaskId.of(42L));
        when(taskRunRepository.findById(TaskRunId.of(11L))).thenReturn(Optional.of(run));
        when(taskRepository.findByIdOwned(TaskId.of(42L), UserId.of(TestCurrentUser.USER_ID)))
                .thenReturn(Optional.of(task));
        when(agentRunService.appendEvents(eq(11L), eq("device@test"), eq(null), anyList())).thenReturn(2);

        mockMvc.perform(post("/api/device-agent/runs/11/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "executor": "device@test",
                                  "events": [
                                    {
                                      "type": "thread.started",
                                      "payload": "{\\"type\\":\\"thread.started\\"}",
                                      "occurredAt": "2026-10-20T07:50:00Z"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("2"));

        verify(agentRunService).appendEvents(eq(11L), eq("device@test"), eq(null), anyList());
    }

    @Test
    @DisplayName("设备因项目占用等待时任务落为排队并加入设备队列")
    void queuesTaskForDevice() throws Exception {
        Task task = Task.manual(ProjectId.of(3L), "设备任务", "在本机执行", 0);
        task.assignIdentity(TaskId.of(42L));
        when(taskRepository.findByIdOwned(TaskId.of(42L), UserId.of(TestCurrentUser.USER_ID)))
                .thenReturn(Optional.of(task));
        when(agentRunService.queueForDevice(42L)).thenReturn(task);

        mockMvc.perform(post("/api/device-agent/tasks/42/queue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"device-1\"}"))
                .andExpect(status().isOk());

        verify(agentRunService).queueForDevice(42L);
        verify(presenceService).enqueue(
                UserId.of(TestCurrentUser.USER_ID), "device-1", 42L);
    }
}
