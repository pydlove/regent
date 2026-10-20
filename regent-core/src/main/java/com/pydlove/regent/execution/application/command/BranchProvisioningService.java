package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.port.BranchManager;
import com.pydlove.regent.project.domain.repository.ProjectProfile;

/**
 * 首次执行前的分支准备：默认直接在项目目录执行。
 *
 * <p>是否为本任务创建隔离分支，由执行器结构化出参里的 {@code branch.requested} 决定；
 * 平台不在派发前替 AI 预建分支，也不再产生逐任务的分支审批。
 * 项目目录不是 Git 仓库、或是多个仓库的父目录时，分支记录为空。
 */
public class BranchProvisioningService {

    private final BranchManager branchManager;

    public BranchProvisioningService(BranchManager branchManager) {
        this.branchManager = branchManager;
    }

    public BranchPreparation prepare(Task task, ProjectProfile project) {
        if (task.isContinuation()) {
            String branch = task.getBranchName().orElse(null);
            if (branch != null) branchManager.switchTo(project.path(), branch);
            task.dispatch(branch);
            return BranchPreparation.started(branch);
        }
        String branch = branchManager.currentBranch(project.path()).orElse(null);
        task.dispatch(branch);
        return BranchPreparation.started(branch);
    }
}
