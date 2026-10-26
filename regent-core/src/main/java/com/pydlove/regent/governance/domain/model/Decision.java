package com.pydlove.regent.governance.domain.model;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 决策单 —— 核心域的聚合根，也是这套平台最需要被审计的那张纸。
 *
 * <p>它的价值不在「记了一条待办」，而在「事后能回答这条改动是谁放行的」。因此有三条不变量
 * 必须由本对象守住，任何一条被绕过都意味着审计链断了：
 *
 * <ol>
 *   <li><b>只有待批的决策能决议。</b>决策是终局判断，重复决议会让「谁拍的板」变成噪音，
 *       也让「批准之后又驳回」这种矛盾记录进入历史；</li>
 *   <li><b>生效等级不得低于 AI 自评。</b>仲裁只升不降（见 {@code DecisionArbiter}），
 *       这条要在聚合里再守一次 —— 建单时若允许降级，前面那条规则就形同虚设；</li>
 *   <li><b>不可逆的改动不许 AI 自主决断。</b>这条不依赖规则配置：即便阈值配成 L3、
 *       规则一条没命中，只要执行器自己说这次改动不可逆，就必须由人点头。</li>
 * </ol>
 *
 * <p>状态只能通过这些领域方法迁移：没有 setStatus，没有公开 setter。
 * 跨聚合的动作（「批准后任务从 BLOCKED 拉回 RUNNING」）由应用服务显式编排 ——
 * 决策单绝不反过来去改任务，那是 DDD 里最常被破坏的一条纪律。
 */
public class Decision {

    /** 尚未持久化时为 null，由仓储 insert 后通过 {@link #assignIdentity} 回填。 */
    private DecisionId id;

    private final TaskId taskId;
    private final ProjectId projectId;
    private final DecisionLevel aiLevel;
    private final DecisionLevel effectiveLevel;
    private final Long matchedRuleId;
    private final String question;
    private final List<String> options;
    private final String recommendation;
    private final boolean reversible;
    private final String blastRadius;
    private final Instant raisedAt;

    private DecisionStatus status;
    private ResolvedBy resolvedBy;
    private String resolvedChoice;
    private String resolutionNote;
    private Instant resolvedAt;

    private final Instant createdAt;
    private Instant updatedAt;

    private Decision(TaskId taskId,
                     ProjectId projectId,
                     DecisionLevel aiLevel,
                     DecisionLevel effectiveLevel,
                     Long matchedRuleId,
                     String question,
                     List<String> options,
                     String recommendation,
                     boolean reversible,
                     String blastRadius,
                     Instant raisedAt,
                     Instant createdAt) {
        this.taskId = requireNotNull(taskId, "所属任务");
        this.projectId = requireNotNull(projectId, "所属项目");
        this.aiLevel = requireNotNull(aiLevel, "AI 自评等级");
        this.effectiveLevel = requireNotNull(effectiveLevel, "生效等级");
        if (aiLevel.isHigherThan(effectiveLevel)) {
            throw new DomainException(
                    "生效等级不得低于 AI 自评等级：自评 " + aiLevel + "，生效 " + effectiveLevel);
        }
        this.matchedRuleId = matchedRuleId;
        this.question = requireText(question, "待决策问题");
        this.options = options == null ? List.of() : List.copyOf(options);
        this.recommendation = blankToNull(recommendation);
        this.reversible = reversible;
        this.blastRadius = blankToNull(blastRadius);
        this.raisedAt = requireNotNull(raisedAt, "提出时间");
        this.status = DecisionStatus.PENDING;
        this.createdAt = requireNotNull(createdAt, "创建时间");
        this.updatedAt = createdAt;
    }

    /** 提一条待批决策。 */
    public static Decision raise(DecisionRaise raise) {
        Instant now = now();
        return new Decision(
                raise.taskId(), raise.projectId(), raise.aiLevel(), raise.effectiveLevel(),
                raise.matchedRuleId(), raise.question(), raise.options(), raise.recommendation(),
                raise.reversible(), raise.blastRadius(), now, now);
    }

    /**
     * 从存储重建。只允许基础设施层调用 —— 它跳过了所有状态守卫，
     * 领域层之外的代码走这条路等于绕过状态机。
     */
    public static Decision restore(DecisionSnapshot snapshot) {
        Decision decision = new Decision(
                snapshot.taskId(), snapshot.projectId(), snapshot.aiLevel(), snapshot.effectiveLevel(),
                snapshot.matchedRuleId(), snapshot.question(), snapshot.options(),
                snapshot.recommendation(), snapshot.reversible(), snapshot.blastRadius(),
                snapshot.raisedAt(), snapshot.createdAt());
        decision.id = requireNotNull(snapshot.id(), "决策 ID");
        decision.status = requireNotNull(snapshot.status(), "决策状态");
        decision.resolvedBy = snapshot.resolvedBy();
        decision.resolvedChoice = snapshot.resolvedChoice();
        decision.resolutionNote = snapshot.resolutionNote();
        decision.resolvedAt = snapshot.resolvedAt();
        decision.updatedAt = snapshot.updatedAt();
        return decision;
    }

    /** 人工批准。批准即放行，之后执行器从断点续跑。 */
    public void approve(String choice, String note) {
        resolve(DecisionStatus.APPROVED, DecisionOutcome.byHuman(choice, note));
    }

    /** 人工驳回。理由可选，填写后会作为说明留在决策记录中。 */
    public void reject(String choice, String note) {
        resolve(DecisionStatus.REJECTED, DecisionOutcome.byHuman(choice, note));
    }

    /** 任务被人工终止后关闭残留待批决策。终止本身就是人的决定，不保留为待办。 */
    public void closeByTaskCancellation() {
        reject(null, "任务已终止，决策自动关闭");
    }

    /**
     * AI 自主决断并留痕。
     *
     * @param autoThreshold 项目级自动决策阈值，来自 {@code project.default_decision_level}
     */
    public void autoResolve(DecisionLevel autoThreshold, String choice, String note) {
        requirePending();
        requireNotNull(autoThreshold, "自动决策阈值");
        if (!reversible) {
            throw new DomainException("不可逆的改动不能由 AI 自主决断，必须人工批准：" + question);
        }
        if (!effectiveLevel.isWithin(autoThreshold)) {
            throw new DomainException(
                    "生效等级 " + effectiveLevel + " 超出自动决策阈值 " + autoThreshold + "，必须人工批准");
        }
        applyResolution(DecisionStatus.AUTO_RESOLVED, DecisionOutcome.byAi(choice, note));
    }

    private void resolve(DecisionStatus target, DecisionOutcome outcome) {
        requirePending();
        applyResolution(target, outcome);
    }

    private void applyResolution(DecisionStatus target, DecisionOutcome outcome) {
        String choice = blankToNull(outcome.choice());
        if (choice != null && choice.length() > 64) {
            throw new DomainException("决议选项超长（上限 64 字符）：" + choice.length());
        }
        this.status = target;
        this.resolvedBy = outcome.by();
        this.resolvedChoice = choice;
        this.resolutionNote = blankToNull(outcome.note());
        this.resolvedAt = now();
        this.updatedAt = this.resolvedAt;
    }

    private void requirePending() {
        if (this.status != DecisionStatus.PENDING) {
            throw new DomainException(
                    "决策已决议（" + this.status + "，由 " + this.resolvedBy + " 在 " + this.resolvedAt
                            + "），不能重复决议");
        }
    }

    /** 是否还需要人来拍板。列表、通知与调度都问这一个方法，别各自去比 status。 */
    public boolean isPending() {
        return this.status == DecisionStatus.PENDING;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value.trim();
    }

    /** 主键回填，只允许仓储调用，且只能分配一次。 */
    public void assignIdentity(DecisionId id) {
        if (this.id != null) {
            throw new DomainException("决策 ID 已分配，不能重复分配：" + this.id);
        }
        this.id = requireNotNull(id, "决策 ID");
    }

    public DecisionId getId() {
        return id;
    }

    public TaskId getTaskId() {
        return taskId;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public DecisionLevel getAiLevel() {
        return aiLevel;
    }

    public DecisionLevel getEffectiveLevel() {
        return effectiveLevel;
    }

    public Optional<Long> getMatchedRuleId() {
        return Optional.ofNullable(matchedRuleId);
    }

    public String getQuestion() {
        return question;
    }

    public List<String> getOptions() {
        return options;
    }

    public Optional<String> getRecommendation() {
        return Optional.ofNullable(recommendation);
    }

    public boolean isReversible() {
        return reversible;
    }

    public Optional<String> getBlastRadius() {
        return Optional.ofNullable(blastRadius);
    }

    public DecisionStatus getStatus() {
        return status;
    }

    public Optional<ResolvedBy> getResolvedBy() {
        return Optional.ofNullable(resolvedBy);
    }

    public Optional<String> getResolvedChoice() {
        return Optional.ofNullable(resolvedChoice);
    }

    public Optional<String> getResolutionNote() {
        return Optional.ofNullable(resolutionNote);
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public Optional<Instant> getResolvedAt() {
        return Optional.ofNullable(resolvedAt);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
