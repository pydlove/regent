package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.application.query.ExecutionSettingsView;
import com.pydlove.regent.execution.domain.model.ExecutionSettings;
import com.pydlove.regent.execution.domain.repository.ExecutionSettingsRepository;
import com.pydlove.regent.execution.domain.service.ExecutionConcurrencyGate;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 平台执行设置的默认值、持久化与运行时闸门同步。 */
class ExecutionSettingsServiceTest {

    private final MemoryRepository repository = new MemoryRepository();
    private final ExecutionConcurrencyGate gate = new ExecutionConcurrencyGate(3);

    private ExecutionSettingsService service;

    @BeforeEach
    void setUp() {
        service = new ExecutionSettingsService(repository, gate, 2);
    }

    @Test
    @DisplayName("未保存时采用启动默认值")
    void 未保存采用默认值() {
        ExecutionSettingsView view = service.current();

        assertEquals(2, view.maxConcurrentTasks());
        assertEquals(0, view.runningTaskCount());
        assertEquals(2, gate.limit());
    }

    @Test
    @DisplayName("保存后立即更新运行上限并持久化")
    void 保存后立即生效() {
        repository.settings = new ExecutionSettings(4);

        ExecutionSettingsView view = service.update(5);

        assertEquals(5, view.maxConcurrentTasks());
        assertEquals(5, repository.settings.maxConcurrentTasks());
        assertEquals(5, gate.limit());
    }

    @Test
    @DisplayName("调低上限不终止已有任务，只阻止新任务取得许可")
    void 调低上限只阻止新任务() {
        ExecutionConcurrencyGate.Permit first = gate.tryAcquire().orElseThrow();
        gate.tryAcquire().orElseThrow();

        ExecutionSettingsView view = service.update(1);

        assertEquals(2, view.runningTaskCount());
        assertTrue(gate.tryAcquire().isEmpty());

        first.release();
        assertTrue(gate.tryAcquire().isEmpty(), "活跃数降到上限之前仍不能放行新任务");
    }

    @Test
    @DisplayName("越界值由领域对象拒绝，不落库")
    void 越界值拒绝() {
        assertThrows(DomainException.class, () -> service.update(0));
        assertThrows(DomainException.class, () -> service.update(65));
        assertNull(repository.settings);
    }

    @Test
    @DisplayName("重启后恢复的活跃租约照常占用并发名额")
    void 恢复活跃租约() {
        gate.updateLimit(1);

        ExecutionConcurrencyGate.Permit restored = gate.restoreActive();

        assertTrue(gate.tryAcquire().isEmpty());
        restored.release();
        assertTrue(gate.tryAcquire().isPresent());
    }

    private static final class MemoryRepository implements ExecutionSettingsRepository {

        private ExecutionSettings settings;

        @Override
        public Optional<ExecutionSettings> find() {
            return Optional.ofNullable(settings);
        }

        @Override
        public void save(ExecutionSettings settings) {
            this.settings = settings;
        }
    }
}
