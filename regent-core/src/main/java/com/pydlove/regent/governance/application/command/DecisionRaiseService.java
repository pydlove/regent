package com.pydlove.regent.governance.application.command;

import com.pydlove.regent.governance.domain.model.ArbitrationInput;
import com.pydlove.regent.governance.domain.model.ArbitrationResult;
import com.pydlove.regent.governance.domain.model.Decision;
import com.pydlove.regent.governance.domain.model.DecisionRaise;
import com.pydlove.regent.governance.domain.model.DecisionRule;
import com.pydlove.regent.governance.domain.repository.DecisionRepository;
import com.pydlove.regent.governance.domain.repository.DecisionRuleRepository;
import com.pydlove.regent.governance.domain.service.DecisionArbiter;

import java.util.List;

/**
 * 建单用例 —— 把「执行器报了一个需要拍板的点」变成一条可审计的决策。
 *
 * <p>它是核心域唯一的入口：所有决策都必须经过 {@link DecisionArbiter} 的「就高不就低」，
 * 而不是让执行器自评的等级直接生效。等级一旦可以绕过仲裁，整套分级机制就只剩下形式。
 *
 * <h2>自动决断的边界</h2>
 * 只有同时满足三件事才自动放行：生效等级落在项目阈值内、规则没把它抬到阈值之上、
 * 且改动可逆。第三条不依赖任何配置 —— 不可逆的改动必须由人点头，这是平台的最后一道底线。
 * 阈值内但不可逆时，决策单留在待批，人会被真的问到。
 */
public class DecisionRaiseService {

    /** AI 自主决断时，没有建议可依据的兜底选项。写死在这里而不是让调用方传，避免各处措辞不一。 */
    private static final String FALLBACK_CHOICE = "按建议执行";

    private final DecisionRuleRepository decisionRuleRepository;
    private final DecisionRepository decisionRepository;
    private final DecisionArbiter arbiter;

    public DecisionRaiseService(DecisionRuleRepository decisionRuleRepository,
                                DecisionRepository decisionRepository) {
        this(decisionRuleRepository, decisionRepository, new DecisionArbiter());
    }

    DecisionRaiseService(DecisionRuleRepository decisionRuleRepository,
                         DecisionRepository decisionRepository,
                         DecisionArbiter arbiter) {
        this.decisionRuleRepository = decisionRuleRepository;
        this.decisionRepository = decisionRepository;
        this.arbiter = arbiter;
    }

    public RaisedDecision raise(RaiseDecisionCommand command) {
        List<DecisionRule> rules = decisionRuleRepository.findEnabledFor(command.projectId().value());
        ArbitrationResult arbitration = arbiter.arbitrate(new ArbitrationInput(
                command.projectId().value(),
                command.aiLevel(),
                command.autoThreshold(),
                command.changedPaths(),
                decisionTextOf(command),
                command.reversible(),
                rules));

        Decision decision = Decision.raise(new DecisionRaise(
                command.taskId(),
                command.projectId(),
                command.aiLevel(),
                arbitration.effectiveLevel(),
                arbitration.matchedRuleId(),
                command.question(),
                command.options(),
                command.recommendation(),
                command.reversible(),
                command.blastRadius()));

        if (autoResolvable(arbitration, command)) {
            decision.autoResolve(command.autoThreshold(), choiceOf(command), noteOf(command, arbitration));
        }

        decisionRepository.save(decision);
        return new RaisedDecision(decision, arbitration);
    }

    private static boolean autoResolvable(ArbitrationResult arbitration, RaiseDecisionCommand command) {
        return arbitration.autoResolvable() && command.reversible();
    }

    /**
     * 决议选项。优先取执行器的建议，其次取第一个候选方案，最后兜底。
     *
     * <p>超过 64 字符会被 {@code Decision} 的守卫拒绝，所以这里先截断 ——
     * 这是「选项」这一列的物理限制，不该让整条决策失败。
     */
    private static String choiceOf(RaiseDecisionCommand command) {
        String candidate = command.recommendation();
        if (candidate == null || candidate.isBlank()) {
            candidate = command.options().isEmpty() ? FALLBACK_CHOICE : command.options().get(0);
        }
        String trimmed = candidate.trim();
        return trimmed.length() <= 64 ? trimmed : trimmed.substring(0, 64);
    }

    private static String noteOf(RaiseDecisionCommand command, ArbitrationResult arbitration) {
        StringBuilder note = new StringBuilder("AI 自主决断：");
        if (command.recommendation() != null && !command.recommendation().isBlank()) {
            note.append(command.recommendation().trim());
        } else {
            note.append("无建议，按候选方案执行");
        }
        note.append("（AI 自评 ").append(arbitration.aiLevel())
                .append("，生效 ").append(arbitration.effectiveLevel())
                .append("，阈值 ").append(arbitration.autoThreshold());
        if (arbitration.matchedRuleName() != null) {
            note.append("，命中规则：").append(arbitration.matchedRuleName());
        }
        note.append('）');
        return note.toString();
    }

    /** 关键词规则的匹配文本：问题 + 建议 + 候选方案。宁可多匹配一点，也不要漏掉本该升级的关键词。 */
    private static String decisionTextOf(RaiseDecisionCommand command) {
        StringBuilder text = new StringBuilder(command.question());
        if (command.recommendation() != null) {
            text.append('\n').append(command.recommendation());
        }
        command.options().forEach(option -> text.append('\n').append(option));
        return text.toString();
    }
}
