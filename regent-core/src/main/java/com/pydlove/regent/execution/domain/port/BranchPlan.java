package com.pydlove.regent.execution.domain.port;

/**
 * 分支准备计划 —— 只回答「用哪条分支、是否需要新建」，不执行任何 Git 写操作。
 *
 * <p>把「检查」与「创建」分开是为了让审批发生在副作用之前：先拿到计划，发现需要新建时
 * 挂起等人；人批准后再调用 {@link BranchManager#createFor(String, TaskId)}。若把两件事
 * 合成一个方法，审批还没发生分支就已经建好了。
 */
public record BranchPlan(String branchName, boolean requiresCreation) {

    public BranchPlan {
        if (branchName == null || branchName.isBlank()) {
            throw new IllegalArgumentException("分支名不能为空");
        }
    }
}
