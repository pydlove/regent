package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskAdjustment;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.repository.TaskAdjustmentRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.util.List;

/**
 * 执行中调整方向的写侧用例。
 *
 * <p>新增调整时先锁目标任务行，再插入调整。任务完成路径也按同一顺序先锁任务、再检查待调整，
 * 因此「用户在完成瞬间提交」不会出现任务已 DONE、调整仍无人处理的静默丢失。
 */
public class TaskAdjustmentService {

    private final TaskRepository taskRepository;
    private final TaskAdjustmentRepository adjustmentRepository;
    private final TransactionRunner transactionRunner;

    public TaskAdjustmentService(TaskRepository taskRepository,
                                 TaskAdjustmentRepository adjustmentRepository,
                                 TransactionRunner transactionRunner) {
        this.taskRepository = taskRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.transactionRunner = transactionRunner;
    }

    public TaskAdjustment add(AddTaskAdjustmentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少调整方向参数");
        }
        return transactionRunner.inTransaction(() -> addInTransaction(command));
    }

    public List<TaskAdjustment> list(TaskId taskId) {
        return adjustmentRepository.findByTask(taskId);
    }

    private TaskAdjustment addInTransaction(AddTaskAdjustmentCommand command) {
        taskRepository.findByIdOwned(command.taskId(), command.ownerId())
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + command.taskId()));
        Task target = taskRepository.findByIdForUpdate(command.taskId())
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + command.taskId()));
        target.requireAcceptsAdjustment();

        boolean hasSource = command.sourceTaskId() != null;
        boolean hasTemporary = command.temporaryContent() != null && !command.temporaryContent().isBlank();
        if (hasSource == hasTemporary) {
            throw new DomainException("来源任务与临时调整方向必须二选一");
        }

        TaskAdjustment adjustment;
        if (hasSource) {
            if (command.sourceTaskId().equals(target.getId())) {
                throw new DomainException("来源任务不能是目标任务本身");
            }
            Task source = taskRepository.findByIdOwned(command.sourceTaskId(), command.ownerId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "来源任务不存在：" + command.sourceTaskId()));
            if (!source.getProjectId().equals(target.getProjectId())) {
                throw new DomainException("来源任务必须与目标任务属于同一项目");
            }
            adjustment = TaskAdjustment.fromTask(target.getId(), source.getId(),
                    source.getTitle(), source.getPrompt());
        } else {
            adjustment = TaskAdjustment.temporary(target.getId(), command.temporaryContent());
        }
        return adjustmentRepository.save(adjustment);
    }
}
