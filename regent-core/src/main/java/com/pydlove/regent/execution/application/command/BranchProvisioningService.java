package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.port.BranchManager;
import com.pydlove.regent.execution.domain.port.BranchPlan;
import com.pydlove.regent.governance.application.command.DecisionRaiseService;
import com.pydlove.regent.governance.application.command.RaiseDecisionCommand;
import com.pydlove.regent.governance.application.command.RaisedDecision;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.shared.kernel.DecisionLevel;

import java.util.List;

/**
 * 首次执行前的分支准备。审批与 Git 写操作在这里被明确拆成两步。
 *
 * <p>规则只有一条：<b>新分支必须先有人批准</b>。已存在的任务分支可以直接复用；分支隔离关闭时
 * 使用当前分支，也不会新建。需要新建时，本类先落一条不可自动决议的 L2 决策并把任务置为
 * {@code BLOCKED}，批准前 {@link BranchManager#createFor(String, com.pydlove.regent.execution.domain.model.TaskId)}
 * 根本不会被调用。
 *
 * <p>两个执行入口（平台内部 codex 与外部 Agent）共用本类，避免一条路径审批、另一条路径绕过。
 */
public class BranchProvisioningService {

    private final BranchManager branchManager;
    private final DecisionRaiseService decisionRaiseService;

    public BranchProvisioningService(BranchManager branchManager,
                                     DecisionRaiseService decisionRaiseService) {
        this.branchManager = branchManager;
        this.decisionRaiseService = decisionRaiseService;
    }

    public BranchPreparation prepare(Task task, ProjectProfile project) {
        BranchPlan plan = branchManager.plan(project.path(), task.getId());
        if (plan.requiresCreation() && !task.isBranchCreationApproved()) {
            RaisedDecision raised = requestApproval(task, project, plan.branchName());
            task.requestBranchApproval(raised.decision().getId());
            return BranchPreparation.awaitingApproval(raised.decision().getId());
        }

        String branch = branchManager.createFor(project.path(), task.getId());
        task.dispatch(branch);
        return BranchPreparation.started(branch);
    }

    private RaisedDecision requestApproval(Task task, ProjectProfile project, String branchName) {
        return decisionRaiseService.raise(new RaiseDecisionCommand(
                task.getId(),
                task.getProjectId(),
                project.defaultDecisionLevel(),
                DecisionLevel.L2,
                "任务 #" + task.getId().value() + " 请求创建并切换到 Git 分支 "
                        + branchName + "，是否批准？",
                List.of("批准创建分支 " + branchName, "不创建分支"),
                "该分支只用于隔离本任务的改动，不会推送远端，也不会修改主干。",
                true,
                "只影响项目工作区的本地分支，不涉及远端仓库。",
                List.of(),
                true));
    }
}
