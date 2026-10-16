package com.pydlove.regent.governance.domain.repository;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.governance.domain.model.Decision;
import com.pydlove.regent.governance.domain.model.DecisionId;

import java.util.Optional;

/**
 * 决策单聚合仓储 —— 「取聚合来改」的那一侧。
 *
 * <p>与只读的 {@link DecisionQueryRepository} 并存而不是合并：读模型要连表拿任务标题、项目名，
 * 聚合只需要自己那一行的十九个字段。合成一个接口，聚合的加载迟早会顺手把展示字段也带回来，
 * 然后在某个不该知道「项目叫什么名」的地方出现一个 projectName。
 */
public interface DecisionRepository {

    Optional<Decision> findById(DecisionId id);

    /** 新增或整体更新。新增时由实现回填数据库生成的主键（{@code Decision.assignIdentity}）。 */
    void save(Decision decision);

    /**
     * 取任务最近一条已决议的决策 —— 断点续跑要靠它知道人拍了什么板。
     *
     * <p>只查已决议的：待批的那条是「问题」，不是「结论」，拿它去续跑等于把问题当答案。
     */
    Optional<Decision> findLatestResolvedByTask(TaskId taskId);
}
