package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.ExecutionDevice;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.ExecutionDeviceRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 设备心跳与浏览器显式派发队列。
 *
 * <p>设备心跳落库，进程内只保留“浏览器刚刚指定给这台设备”的短队列。后端重启后短队列会消失，
 * 但下一次心跳仍会从 {@code task.status = QUEUED} 重新发现可领取任务。
 */
public class DevicePresenceService {

    private static final Duration ONLINE_TTL = Duration.ofSeconds(45);
    private static final int TASKS_PER_HEARTBEAT = 1;

    private final ExecutionDeviceRepository deviceRepository;
    private final TaskRepository taskRepository;
    private final DeviceRestartRecoveryHandler restartRecoveryHandler;
    private final Map<String, ConcurrentLinkedQueue<Long>> jobs = new ConcurrentHashMap<>();

    public DevicePresenceService(ExecutionDeviceRepository deviceRepository,
                                 TaskRepository taskRepository,
                                 DeviceRestartRecoveryHandler restartRecoveryHandler) {
        this.deviceRepository = deviceRepository;
        this.taskRepository = taskRepository;
        this.restartRecoveryHandler = restartRecoveryHandler;
    }

    public DevicePresence heartbeat(UserId ownerId, DeviceHeartbeat heartbeat) {
        ExecutionDevice found = deviceRepository.findByDeviceId(heartbeat.deviceId()).orElse(null);
        if (found != null && !found.ownerId().equals(ownerId)) {
            throw new EntityNotFoundException("设备不存在：" + heartbeat.deviceId());
        }
        ExecutionDevice existing = found;
        if (existing != null && instanceChanged(existing, heartbeat.agentInstanceId())) {
            restartRecoveryHandler.recover(
                    heartbeat.deviceId(),
                    "设备代理进程已重启，旧实例留下的执行已中断并重新排队");
        }
        ExecutionDevice device = ExecutionDevice.heartbeat(
                existing,
                ownerId,
                heartbeat.deviceId(),
                heartbeat.hostname(),
                heartbeat.platform(),
                heartbeat.executor(),
                heartbeat.agentInstanceId(),
                new ExecutionDevice.CodexStatus(
                        heartbeat.codex().available(),
                        heartbeat.codex().executablePath(),
                        heartbeat.codex().version(),
                        heartbeat.codex().message(),
                        heartbeat.codex().checkedAt() == null
                                ? heartbeat.observedAt()
                                : heartbeat.codex().checkedAt()),
                heartbeat.clientRelease(),
                heartbeat.shellRelease(),
                heartbeat.observedAt());
        deviceRepository.save(device);

        List<Long> taskIds = nextAssignedTasks(ownerId, device.deviceId());
        if (taskIds.isEmpty()) {
            taskIds = taskRepository.findQueued(ownerId, TASKS_PER_HEARTBEAT).stream()
                    .map(Task::getId)
                    .map(TaskId::value)
                    .toList();
        }
        return new DevicePresence(device, true, taskIds);
    }

    public Optional<DevicePresence> current(UserId ownerId) {
        Instant now = now();
        return deviceRepository.findByOwner(ownerId).stream()
                .max(Comparator.comparing(ExecutionDevice::lastSeenAt))
                .map(device -> new DevicePresence(
                        device, device.isOnline(now, ONLINE_TTL), List.of()));
    }

    public void enqueue(UserId ownerId, String deviceId, long taskId) {
        ExecutionDevice device = deviceRepository.findByDeviceId(required(deviceId, "设备 ID"))
                .filter(item -> item.ownerId().equals(ownerId))
                .orElseThrow(() -> new EntityNotFoundException("设备不存在：" + deviceId));
        if (!device.isOnline(now(), ONLINE_TTL)) {
            throw new DomainException("当前设备代理不在线，请重新打开或启动设备代理");
        }
        Task task = taskRepository.findByIdOwned(TaskId.of(taskId), ownerId)
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + taskId));
        if (task.getStatus() != TaskStatus.PENDING && task.getStatus() != TaskStatus.QUEUED) {
            throw new DomainException("任务当前不能派发到设备：" + task.getStatus());
        }
        ConcurrentLinkedQueue<Long> queue =
                jobs.computeIfAbsent(device.deviceId(), ignored -> new ConcurrentLinkedQueue<>());
        if (!queue.contains(taskId)) {
            queue.add(taskId);
        }
    }

    public void markClaimed(UserId ownerId, String deviceId, Instant claimedAt) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        deviceRepository.findByDeviceId(deviceId)
                .filter(device -> device.ownerId().equals(ownerId))
                .map(device -> device.markClaimed(claimedAt))
                .ifPresent(deviceRepository::save);
    }

    public ExecutionDevice requireOnline(UserId ownerId, String deviceId) {
        ExecutionDevice device = deviceRepository.findByDeviceId(required(deviceId, "设备 ID"))
                .filter(item -> item.ownerId().equals(ownerId))
                .orElseThrow(() -> new EntityNotFoundException("设备不存在：" + deviceId));
        if (!device.isOnline(now(), ONLINE_TTL)) {
            throw new DomainException("设备已离线，请重新连接后再派发");
        }
        return device;
    }

    private List<Long> nextAssignedTasks(UserId ownerId, String deviceId) {
        ConcurrentLinkedQueue<Long> queue =
                jobs.computeIfAbsent(deviceId, ignored -> new ConcurrentLinkedQueue<>());
        List<Long> taskIds = new ArrayList<>();
        for (int i = 0; i < TASKS_PER_HEARTBEAT; i++) {
            Long taskId = queue.poll();
            if (taskId == null) {
                break;
            }
            boolean deliverable = taskRepository.findByIdOwned(TaskId.of(taskId), ownerId)
                    .map(task -> task.getStatus() == TaskStatus.PENDING
                            || task.getStatus() == TaskStatus.QUEUED)
                    .orElse(false);
            if (deliverable) {
                taskIds.add(taskId);
            } else {
                i--;
            }
        }
        return taskIds;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value.trim();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static boolean instanceChanged(ExecutionDevice existing, String currentInstanceId) {
        return existing.agentInstanceId() != null
                && currentInstanceId != null
                && !currentInstanceId.isBlank()
                && !existing.agentInstanceId().equals(currentInstanceId);
    }
}
