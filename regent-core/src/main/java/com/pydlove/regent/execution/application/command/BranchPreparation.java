package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.governance.domain.model.DecisionId;

/**
 * 一次分支准备的结果。
 *
 * <p>{@code ready=false} 表示没有执行任何 Git 写操作，任务已经挂起等待人工批准；
 * 调用方必须停止后续执行提交，而不是把「已生成审批单」当成「已派发」。
 * {@code branchName} 可以为空：项目不在 Git 仓库中执行时没有分支名。
 */
public record BranchPreparation(boolean ready, String branchName, DecisionId decisionId) {

    public static BranchPreparation started(String branchName) {
        return new BranchPreparation(true, branchName, null);
    }

    public static BranchPreparation awaitingApproval(DecisionId decisionId) {
        return new BranchPreparation(false, null, decisionId);
    }
}
