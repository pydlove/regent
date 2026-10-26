package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.bug.domain.model.Bug;
import com.pydlove.regent.bug.domain.model.BugId;
import com.pydlove.regent.bug.domain.repository.BugRepository;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.shared.domain.port.TaskStandardBinder;

/**
 * 缺陷派生修复任务的应用编排。
 *
 * <p>缺陷单与任务属于两个聚合，任何一边都不能直接改另一边。这里在一个事务里锁住缺陷单、
 * 创建 BUG_FIX 任务、回填修复任务 ID，再调用统一的任务调度器认领执行；执行线程由调度器
 * 推迟到事务提交后启动。
 */
public class BugFixDispatchService {

    private final BugRepository bugRepository;
    private final TaskRepository taskRepository;
    private final TaskDispatchService taskDispatchService;
    private final TransactionRunner transactionRunner;
    private final TaskStandardBinder taskStandardBinder;

    public BugFixDispatchService(BugRepository bugRepository,
                                 TaskRepository taskRepository,
                                 TaskDispatchService taskDispatchService,
                                 TransactionRunner transactionRunner) {
        this(bugRepository, taskRepository, taskDispatchService, transactionRunner, null);
    }

    public BugFixDispatchService(BugRepository bugRepository,
                                 TaskRepository taskRepository,
                                 TaskDispatchService taskDispatchService,
                                 TransactionRunner transactionRunner,
                                 TaskStandardBinder taskStandardBinder) {
        this.bugRepository = bugRepository;
        this.taskRepository = taskRepository;
        this.taskDispatchService = taskDispatchService;
        this.transactionRunner = transactionRunner;
        this.taskStandardBinder = taskStandardBinder;
    }

    public Task dispatchFix(BugId bugId) {
        return transactionRunner.inTransaction(() -> {
            Bug bug = bugRepository.findByIdForUpdate(bugId)
                    .orElseThrow(() -> new EntityNotFoundException("缺陷单不存在：" + bugId));
            Task task = Task.fromBug(
                    bug.getProjectId(),
                    bug.getId(),
                    bug.getTitle(),
                    buildPrompt(bug),
                    0);

            taskRepository.save(task);
            if (taskStandardBinder != null) {
                taskStandardBinder.bindDefaults(task.getId().value(), task.getProjectId().value());
            }
            bug.requestFix(task.getId());
            bugRepository.save(bug);
            return taskDispatchService.dispatchAfterCommit(task.getId());
        });
    }

    private static String buildPrompt(Bug bug) {
        StringBuilder prompt = new StringBuilder()
                .append("请修复以下缺陷，并完成必要的验证。\n\n")
                .append("缺陷单：#").append(bug.getId().value()).append('\n')
                .append("标题：").append(bug.getTitle()).append('\n')
                .append("严重级：").append(bug.getSeverity()).append('\n');
        bug.getReproduction().ifPresent(value -> prompt.append("复现步骤：").append(value).append('\n'));
        bug.getFilePath().ifPresent(value -> prompt.append("定位文件：").append(value).append('\n'));
        bug.getDetail().ifPresent(value -> prompt.append("补充说明：").append(value).append('\n'));
        prompt.append("\n完成后请在总结中说明改动内容和验证结果。");
        return prompt.toString();
    }
}
