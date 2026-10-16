package com.pydlove.regent.governance.domain.repository;

import com.pydlove.regent.governance.domain.model.DecisionRule;

import java.util.List;

/**
 * 分级规则仓储（只读）。
 *
 * <p>规则是仲裁的输入，不是聚合：没有生命周期，也不该在执行链路里被顺手改掉。
 * 维护界面将来走单独的写侧用例，改完靠规则表的 enabled / priority 字段生效。
 */
public interface DecisionRuleRepository {

    /**
     * 取对某项目生效的启用规则：全局规则 + 该项目自己的规则。
     *
     * <p>这里按项目筛一遍只是省几行传输，<b>不是</b>最终裁决：
     * {@code DecisionArbiter} 还会自己再过滤一次。两处过滤不是重复设计 ——
     * 仲裁器必须能在「拿到全量规则」的情况下依然正确，否则它的单测就得依赖 SQL 的行为。
     */
    List<DecisionRule> findEnabledFor(Long projectId);
}
