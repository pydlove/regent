package com.pydlove.regent.execution.domain.port;

import com.pydlove.regent.execution.domain.model.TaskId;

/**
 * 分支管理端口 —— 「绝不直接改主干」这条安全规则的落点。
 *
 * <p>命名策略（{@code codex/task-N}）属于这个端口的实现，不写死在聚合里：
 * 将来要改成「从某个基线切出来」或者「每个需求一个分支」，改的是适配器。
 */
public interface BranchManager {

    /**
     * 只检查分支现状，不创建、不切换。
     *
     * <p>首次派发先用它判断是否真的需要新建分支；需要时先走人工审批，批准后再调用
     * {@link #createFor(String, TaskId)}。检查与创建分开，审批才可能发生在 Git 副作用之前。
     */
    BranchPlan plan(String projectPath, TaskId taskId);

    /**
     * 为任务准备一条独立分支并切过去。
     *
     * @param projectPath 工作区绝对路径
     * @return 实际使用的分支名 —— 关掉隔离时是当前分支名，也如实返回，绝不编一个没切过去的名字
     */
    String createFor(String projectPath, TaskId taskId);

    /**
     * 切回任务既有分支，续跑前调用。
     *
     * <p>决策可能挂起几小时甚至跨天，期间工作区完全可能被人切到别处。续跑时若不做这一步，
     * 上一轮的改动留在 {@code codex/task-N} 上，而新一轮却跑在别的分支上 ——
     * 两边都改、谁也没接着谁，最后合并时才发现。
     */
    void switchTo(String projectPath, String branchName);
}
