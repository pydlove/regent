package com.pydlove.regent.governance.infrastructure.persistence;

import com.pydlove.regent.governance.domain.model.DecisionRule;
import com.pydlove.regent.governance.domain.model.RuleType;
import com.pydlove.regent.governance.domain.repository.DecisionRuleRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;

/**
 * 分级规则查询的 MyBatis 实现。
 *
 * <p><b>解析不出来一律报错，不跳过。</b>规则读不懂时若静默忽略，后果是决策被自动执行 ——
 * 一条本该把等级抬到 L3 的规则没生效，而界面上看不出任何异常。宁可让这次仲裁失败、
 * 任务停在原地等人看，也不能让它带着一个「少了条规则」的结论继续跑。这是 fail-closed 的
 * 又一处落点，与 {@code DecisionArbiter} 对通配表达式写错的态度一致。
 */
@Repository
public class MyBatisDecisionRuleRepository implements DecisionRuleRepository {

    private final DecisionRuleMapper decisionRuleMapper;

    public MyBatisDecisionRuleRepository(DecisionRuleMapper decisionRuleMapper) {
        this.decisionRuleMapper = decisionRuleMapper;
    }

    @Override
    public List<DecisionRule> findEnabledFor(Long projectId) {
        return decisionRuleMapper.selectEnabledFor(projectId).stream()
                .map(MyBatisDecisionRuleRepository::toDomain)
                .toList();
    }

    private static DecisionRule toDomain(DecisionRuleRow row) {
        return new DecisionRule(
                row.getId(),
                row.getProjectId(),
                row.getName(),
                parseType(row),
                row.getPattern(),
                parseLevel(row),
                row.getPriority() == null ? 0 : row.getPriority(),
                Boolean.TRUE.equals(row.getEnabled()));
    }

    private static RuleType parseType(DecisionRuleRow row) {
        try {
            return RuleType.valueOf(row.getRuleType().trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new DomainException("规则[" + row.getName() + "]的匹配方式无法识别：" + row.getRuleType(), e);
        }
    }

    private static DecisionLevel parseLevel(DecisionRuleRow row) {
        try {
            return DecisionLevel.parse(row.getTargetLevel());
        } catch (DomainException e) {
            throw new DomainException("规则[" + row.getName() + "]的目标等级无法识别：" + row.getTargetLevel(), e);
        }
    }
}
