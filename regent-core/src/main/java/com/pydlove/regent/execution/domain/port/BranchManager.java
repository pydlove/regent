package com.pydlove.regent.execution.domain.port;

import com.pydlove.regent.execution.domain.model.TaskId;

import java.util.Optional;

/**
 * 分支管理端口 —— 在项目根目录本身是有效 Git 仓库时提供分支隔离能力。
 *
 * <p>项目目录可以不是 Git 仓库，也可以是多个 Git 仓库的父目录。这两种情况下
 * {@link #currentBranch(String)} 返回空，任务直接在目录本身执行；分支字段为空是合法状态，
 * 不是派发失败。
 */
public interface BranchManager {

    /**
     * 当前工作区所在分支。
     *
     * <p>只在项目根目录本身是 Git 工作区、且 {@code HEAD} 能解析出一次提交时返回分支名。
     * 非 Git 目录、多仓库父目录、空仓库和游离 {@code HEAD} 都返回空。
     */
    Optional<String> currentBranch(String projectPath);

    /**
     * 当前工作区的提交基线。审阅快照用它回答“这次任务从哪段代码开始改”。
     *
     * <p>非 Git 目录、空仓库或多仓库父目录无法给出稳定提交，返回空而不伪造 ref。
     */
    default Optional<String> currentCommit(String projectPath) {
        return Optional.empty();
    }

    /**
     * 任务执行完成后，AI 明确要求隔离时才创建功能分支；未提交改动会随工作区一起带过去。
     *
     * <p>非 Git 目录、多仓库父目录和空仓库无法在根目录创建分支，此时安静返回空，
     * 不把「当前目录不支持分支」升级成任务失败。
     */
    Optional<String> createRequestedFeature(String projectPath, TaskId taskId, String requestedName);

    /**
     * 切回任务既有分支，续跑前调用。
     *
     * <p>决策可能挂起几小时甚至跨天，期间工作区完全可能被人切到别处。续跑时若不做这一步，
     * 上一轮的改动留在 {@code codex/task-N} 上，而新一轮却跑在别的分支上 ——
     * 两边都改、谁也没接着谁，最后合并时才发现。
     *
     * <p>没有分支名、项目根目录不是有效 Git 仓库或分支已不存在时跳过切换，
     * 让任务继续在项目目录中执行。
     */
    void switchTo(String projectPath, String branchName);
}
