package com.secsmart.regent.governance.domain.service;

import com.secsmart.regent.governance.domain.model.ArbitrationInput;
import com.secsmart.regent.governance.domain.model.ArbitrationResult;
import com.secsmart.regent.governance.domain.model.DecisionRule;
import com.secsmart.regent.shared.domain.DomainException;
import com.secsmart.regent.shared.kernel.DecisionLevel;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.PatternSyntaxException;

/**
 * 决策分级仲裁器 —— 本项目的核心域，唯一不能出错的地方。
 *
 * <p>职责只有一件：把「AI 自评的等级」与「规则命中的等级」合并成生效等级。合并规则也只有一条：
 * <b>就高不就低</b>。
 *
 * <h2>为什么规则只能升级、绝不能降级</h2>
 * 允许规则把 AI 自评的高等级压回去，是这套机制里最危险的一个动作。执行器之所以报 L3，通常
 * 是因为它在改动内容里识别到了某种风险；若一条路径通配就能否掉这个判断，等于用正则表达式
 * 覆盖了风险识别。因此本实现<b>刻意不提供降级能力</b>：{@link DecisionLevel#higherOf} 的存在
 * 就是为了让「降级」在代码层面也写不出来。
 *
 * <p>若将来确实需要降低噪音（例如纯文档改动被误报为 L2），那也应该作为一个独立设计、
 * 配套专门的测试与配置开关引入，而不是顺手加在这个循环里。
 *
 * <h2>匹配失败一律失败关闭（fail-closed）</h2>
 * 规则里的通配表达式写错时抛 {@link DomainException}，而不是当作「未命中」跳过。跳过一条本该
 * 提升等级的规则，后果是决策被自动执行；让任务报错停下的代价远小于此。
 */
public final class DecisionArbiter {

    private static final FileSystem FILE_SYSTEM = FileSystems.getDefault();

    /**
     * 仲裁一次决策。
     *
     * @param input 仲裁所需的事实，见 {@link ArbitrationInput}
     * @return 生效等级与命中的规则
     * @throws DomainException 规则配置非法（通配表达式写错、可逆性 pattern 不是布尔值）
     */
    public ArbitrationResult arbitrate(ArbitrationInput input) {
        Objects.requireNonNull(input, "仲裁输入不能为空");

        List<DecisionRule> applicable = input.rules().stream()
                .filter(DecisionRule::enabled)
                .filter(rule -> rule.isGlobal() || Objects.equals(rule.projectId(), input.projectId()))
                .sorted(Comparator
                        .comparingInt(DecisionRule::priority).reversed()
                        .thenComparing(DecisionRule::id, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        DecisionLevel effective = input.aiLevel();
        DecisionRule matched = null;

        for (DecisionRule rule : applicable) {
            if (!matches(rule, input)) {
                continue;
            }
            if (matched == null) {
                matched = rule;
            }
            effective = effective.higherOf(rule.targetLevel());
        }

        return new ArbitrationResult(
                input.aiLevel(),
                effective,
                input.autoThreshold(),
                matched == null ? null : matched.id(),
                matched == null ? null : matched.name(),
                effective.isHigherThan(input.aiLevel()));
    }

    private boolean matches(DecisionRule rule, ArbitrationInput input) {
        return switch (rule.type()) {
            case PATH_GLOB -> input.changedPaths().stream()
                    .anyMatch(path -> matchesGlob(rule.pattern(), path));
            case KEYWORD -> containsIgnoreCase(input.decisionText(), rule.pattern());
            case REVERSIBILITY -> matchesReversibility(rule, input.reversible());
        };
    }

    /**
     * 通配匹配。Java 的 glob 与 Ant 习惯有一处差异：Ant 里以双星号开头的表达式也能匹配根目录下的
     * 文件，Java 的不行（因为没有目录分隔符可匹配）。这个差异会造成「规则写了却静默不生效」，
     * 所以这里补一次去掉前缀的匹配，向规则作者的预期靠齐。
     */
    private static boolean matchesGlob(String pattern, String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        Path candidate;
        try {
            candidate = Path.of(path);
        } catch (InvalidPathException e) {
            throw new DomainException("改动路径无法解析：" + path, e);
        }
        try {
            if (matcher(pattern).matches(candidate)) {
                return true;
            }
            if (pattern.startsWith("**/")) {
                return matcher(pattern.substring(3)).matches(candidate);
            }
            return false;
        } catch (PatternSyntaxException e) {
            throw new DomainException("决策规则的通配表达式非法：" + pattern, e);
        }
    }

    private static PathMatcher matcher(String pattern) {
        return FILE_SYSTEM.getPathMatcher("glob:" + pattern);
    }

    private static boolean containsIgnoreCase(String text, String keyword) {
        return text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private static boolean matchesReversibility(DecisionRule rule, Boolean reversible) {
        if (reversible == null) {
            return false;
        }
        return switch (rule.pattern().trim().toLowerCase(Locale.ROOT)) {
            case "true" -> reversible;
            case "false" -> !reversible;
            default -> throw new DomainException(
                    "REVERSIBILITY 规则的 pattern 只接受 true 或 false，规则[" + rule.name()
                            + "]实际为：" + rule.pattern());
        };
    }
}
