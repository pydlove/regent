package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevicePresenceServiceTest {

    private static final UserId OWNER = UserId.of(1L);
    private static final ProjectId PROJECT = ProjectId.of(1L);

    @TempDir
    Path tempDir;

    private final ExecutionFakes.Devices devices = new ExecutionFakes.Devices();
    private final ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
    private final ExecutionFakes.Runs runs = new ExecutionFakes.Runs();
    private final ExecutionFakes.Transactions transactions = new ExecutionFakes.Transactions();
    private DevicePresenceService service;

    @BeforeEach
    void setUp() throws Exception {
        tasks.wire(runs);
        Path projectPath = tempDir.resolve("project");
        Files.createDirectories(projectPath);
        service = new DevicePresenceService(
                devices,
                tasks,
                (deviceId, reason) -> new DeviceRestartRecoveryService(tasks, runs, transactions)
                        .recover(deviceId, reason)
                        .size());
    }

    @Test
    @DisplayName("设备心跳落库，并可从当前账号重新读取")
    void 心跳持久化() {
        DevicePresence heartbeat = service.heartbeat(OWNER, heartbeat("device-1"));

        assertTrue(heartbeat.online());
        assertNotNull(devices.findByDeviceId("device-1").orElseThrow().lastSeenAt());
        var current = service.current(OWNER).orElseThrow();
        assertEquals("device-1", current.device().deviceId());
        assertTrue(current.online());
    }

    @Test
    @DisplayName("浏览器指定给设备的任务在下一次心跳取走且只取一次")
    void 显式派发任务由心跳取走() {
        var task = ExecutionFakes.task(42L, PROJECT, TaskStatus.PENDING);
        tasks.add(task);
        service.heartbeat(OWNER, heartbeat("device-1"));

        service.enqueue(OWNER, "device-1", 42L);
        DevicePresence first = service.heartbeat(OWNER, heartbeat("device-1"));
        DevicePresence second = service.heartbeat(OWNER, heartbeat("device-1"));

        assertEquals(List.of(42L), first.taskIds());
        assertTrue(second.taskIds().isEmpty());
    }

    @Test
    @DisplayName("其他账号不能凭相同设备号夺取设备心跳")
    void 设备号不能跨账号覆盖() {
        service.heartbeat(OWNER, heartbeat("device-1"));

        assertThrows(
                com.pydlove.regent.shared.domain.EntityNotFoundException.class,
                () -> service.heartbeat(UserId.of(2L), heartbeat("device-1")));

        assertEquals(OWNER, devices.findByDeviceId("device-1").orElseThrow().ownerId());
    }

    @Test
    @DisplayName("设备代理换进程实例后立即中断旧租约并重新排队")
    void 设备实例重启立即恢复() {
        service.heartbeat(OWNER, heartbeat("device-1", "instance-old"));
        var task = ExecutionFakes.task(42L, PROJECT, TaskStatus.RUNNING);
        tasks.add(task);
        TaskRun run = TaskRun.start(task.getId(), 1, "main");
        run.claimBy("device@test", "device-1", Instant.now().plusSeconds(600));
        runs.save(run);

        service.heartbeat(OWNER, heartbeat("device-1", "instance-new"));

        assertEquals(RunStatus.INTERRUPTED, runs.get(run.getId().value()).getStatus());
        assertEquals(TaskStatus.QUEUED, tasks.get(42L).getStatus());
        assertEquals("instance-new",
                devices.findByDeviceId("device-1").orElseThrow().agentInstanceId());
    }

    private static DeviceHeartbeat heartbeat(String deviceId) {
        return heartbeat(deviceId, "instance-1");
    }

    private static DeviceHeartbeat heartbeat(String deviceId, String instanceId) {
        Instant now = Instant.now();
        return new DeviceHeartbeat(
                deviceId,
                "macbook",
                "darwin",
                "regent-device@macbook",
                instanceId,
                new DeviceHeartbeat.CodexStatus(
                        true, "/usr/local/bin/codex", "codex-cli 1.0",
                        "Codex 可执行文件可用", now),
                "R100B002",
                "R100B002",
                now);
    }
}
