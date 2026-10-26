package com.pydlove.regent.execution.application.query;

import com.pydlove.regent.execution.domain.repository.ExecutionControlTowerRepository;
import com.pydlove.regent.execution.domain.repository.ExecutionControlTowerRow;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionControlTowerServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    @Test
    @DisplayName("执行控制塔优先展示失联并按状态汇总")
    void summarizesExecutionHealth() {
        ExecutionControlTowerRepository repository = (ownerId, projectId, limit) -> List.of(
                row("TASK", 1, "RUNNING", NOW.minusSeconds(5), NOW.minusSeconds(1)),
                row("TASK", 2, "RUNNING", NOW.minusSeconds(40), NOW.minusSeconds(40)),
                row("TASK", 4, "RUNNING", NOW.minusSeconds(20), NOW.minusSeconds(20)),
                new ExecutionControlTowerRow(
                        "TASK", 3, 33L, 1, 7L, "项目", "设备失联",
                        "RUNNING", "RUNNING", "agent", "设备 A", null, "执行中",
                        NOW.minusSeconds(60), null, NOW.minusSeconds(20),
                        NOW.minusSeconds(1), NOW.minusSeconds(1)));
        ExecutionControlTowerService service = new ExecutionControlTowerService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        ExecutionControlTowerSnapshot snapshot = service.snapshot(
                UserId.of(1L), ProjectId.of(7L), 100);

        assertEquals(4, snapshot.activeCount());
        assertEquals(3, snapshot.abnormalCount());
        assertEquals("DEVICE_LOST", snapshot.entries().get(0).healthStatus());
        assertEquals("STALLED", snapshot.entries().get(1).healthStatus());
        assertEquals("SLOW", snapshot.entries().get(2).healthStatus());
        assertTrue(snapshot.entries().get(0).stoppable());
    }

    @Test
    @DisplayName("设备租约有效但长时间没有业务事件仍判为疑似卡住")
    void activeLeaseDoesNotHideStalledExecution() {
        ExecutionControlTowerRepository repository = (ownerId, projectId, limit) -> List.of(
                new ExecutionControlTowerRow(
                        "TASK", 8, 8L, 1, 7L, "项目", "心跳正常但模型不动",
                        "RUNNING", "RUNNING", "agent", "设备 A", null, "执行中",
                        NOW.minusSeconds(300), null, NOW.minusSeconds(300),
                        NOW.plusSeconds(60), NOW.minusSeconds(300)));
        ExecutionControlTowerService service = new ExecutionControlTowerService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        var entry = service.snapshot(UserId.of(1L), ProjectId.of(7L), 20)
                .entries().get(0);

        assertEquals("STALLED", entry.healthStatus());
    }

    private static ExecutionControlTowerRow row(String type,
                                                long id,
                                                String status,
                                                Instant startedAt,
                                                Instant lastEventAt) {
        return new ExecutionControlTowerRow(
                type, id, id, 1, 7L, "项目", "任务 " + id,
                status, status, "codex-cli", null, null, "执行中",
                startedAt, null, lastEventAt, null, lastEventAt);
    }
}
