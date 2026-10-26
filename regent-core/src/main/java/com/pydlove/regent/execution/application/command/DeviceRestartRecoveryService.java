package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备代理进程重启恢复。
 *
 * <p>旧进程已经退出，但旧租约通常还没到期。等待租约超时会让任务无谓停留十几分钟，因此设备用
 * 新的实例号发来第一次心跳时，平台立即中断该设备的活跃执行记录，并把仍处于 RUNNING 的任务
 * 放回 QUEUED；随后同一拍心跳即可发现并重新领取任务。
 */
public class DeviceRestartRecoveryService {

    private final TaskRepository taskRepository;
    private final TaskRunRepository taskRunRepository;
    private final TransactionRunner transactionRunner;

    public DeviceRestartRecoveryService(TaskRepository taskRepository,
                                        TaskRunRepository taskRunRepository,
                                        TransactionRunner transactionRunner) {
        this.taskRepository = taskRepository;
        this.taskRunRepository = taskRunRepository;
        this.transactionRunner = transactionRunner;
    }

    public List<TaskRunId> recover(String deviceId, String reason) {
        List<TaskRunId> recovered = new ArrayList<>();
        for (TaskRun candidate : taskRunRepository.findRunningByDevice(deviceId)) {
            boolean changed = transactionRunner.inTransaction(
                    () -> recoverOne(candidate.getId(), deviceId, reason));
            if (changed) {
                recovered.add(candidate.getId());
            }
        }
        return List.copyOf(recovered);
    }

    private boolean recoverOne(com.pydlove.regent.execution.domain.model.TaskRunId runId,
                               String deviceId,
                               String reason) {
        TaskRun run = taskRunRepository.findById(runId).orElse(null);
        if (run == null || run.getStatus() != RunStatus.RUNNING
                || !deviceId.equals(run.getDeviceId())) {
            return false;
        }
        run.interruptByDeviceRestart(deviceId, reason);
        taskRunRepository.save(run);

        Task task = taskRepository.findByIdForUpdate(run.getTaskId()).orElse(null);
        if (task != null && task.getStatus() == TaskStatus.RUNNING) {
            task.requeueAfterInterruption();
            taskRepository.save(task);
        }
        return true;
    }
}
