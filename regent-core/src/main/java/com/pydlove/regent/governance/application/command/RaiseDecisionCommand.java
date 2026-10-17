package com.pydlove.regent.governance.application.command;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.kernel.DecisionLevel;

import java.util.List;
import java.util.Objects;

/**
 * 提出一条决策单所需的全部事实。执行器报什么，这里就原样传什么，不做加工。
 *
 * @param autoThreshold 项目级自动决策阈值，来自 {@code project.default_decision_level}
 * @param changedPaths  本次改动涉及的路径，用于 PATH_GLOB 规则
 * @param requiresHumanApproval 是否无条件要求人工批准；分支创建等平台安全动作必须为 true，
 *                              不受项目阈值影响
 */
public record RaiseDecisionCommand(
        TaskId taskId,
        ProjectId projectId,
        DecisionLevel autoThreshold,
        DecisionLevel aiLevel,
        String question,
        List<String> options,
        String recommendation,
        boolean reversible,
        String blastRadius,
        List<String> changedPaths,
        boolean requiresHumanApproval) {

    /** 执行器上报的普通决策默认仍按分级规则判断是否可自动决议。 */
    public RaiseDecisionCommand(TaskId taskId,
                                ProjectId projectId,
                                DecisionLevel autoThreshold,
                                DecisionLevel aiLevel,
                                String question,
                                List<String> options,
                                String recommendation,
                                boolean reversible,
                                String blastRadius,
                                List<String> changedPaths) {
        this(taskId, projectId, autoThreshold, aiLevel, question, options, recommendation,
                reversible, blastRadius, changedPaths, false);
    }

    public RaiseDecisionCommand {
        Objects.requireNonNull(taskId, "决策必须挂在某个任务上");
        Objects.requireNonNull(projectId, "决策必须属于某个项目");
        Objects.requireNonNull(autoThreshold, "自动决策阈值不能为空");
        Objects.requireNonNull(aiLevel, "AI 自评等级不能为空");
        options = options == null ? List.of() : List.copyOf(options);
        changedPaths = changedPaths == null ? List.of() : List.copyOf(changedPaths);
    }
}
