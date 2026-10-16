package com.pydlove.regent.governance.domain.model;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.kernel.DecisionLevel;

import java.util.List;
import java.util.Objects;

/**
 * 提出一条决策单所需的事实。
 *
 * <p>把入参打包成记录，而不是给 {@code Decision} 写一个十一个参数的工厂方法：后者的调用点
 * 会变成一串位置参数，改动一次字段顺序就会静默传错 —— 编译期一声不吭，运行期写成一条
 * 结论张冠李戴的审计记录。
 *
 * <p>{@code aiLevel} 与 {@code effectiveLevel} 都要传：前者是执行器的自评，后者是仲裁结果。
 * 两者都落库，事后才能回答「这条决策是 AI 拍的还是你批的、依据哪条规则」。
 */
public record DecisionRaise(
        TaskId taskId,
        ProjectId projectId,
        DecisionLevel aiLevel,
        DecisionLevel effectiveLevel,
        Long matchedRuleId,
        String question,
        List<String> options,
        String recommendation,
        boolean reversible,
        String blastRadius) {

    public DecisionRaise {
        Objects.requireNonNull(taskId, "决策必须挂在某个任务上");
        Objects.requireNonNull(projectId, "决策必须属于某个项目");
        Objects.requireNonNull(aiLevel, "AI 自评等级不能为空");
        Objects.requireNonNull(effectiveLevel, "生效等级不能为空");
        options = options == null ? List.of() : List.copyOf(options);
    }
}
