package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.util.List;

/**
 * 按业务意图组织的审阅单元。
 *
 * <p>它属于快照聚合，不能脱离快照单独成立。高风险业务单元默认要求查看；生成代码、锁文件、
 * 格式化和二进制变更则默认折叠，避免把真正的行为变化淹没在文件列表里。
 */
public class ReviewChangeUnit {

    private ReviewChangeUnitId id;
    private final int sequenceNo;
    private final ReviewChangeUnitKind kind;
    private final ReviewRiskLevel riskLevel;
    private final String title;
    private final String reason;
    private final String decisionQuestion;
    private final String moduleName;
    private final List<String> filePaths;
    private final List<ReviewHunk> keyHunks;
    private final boolean requiresView;
    private Instant viewedAt;
    private ReviewUnitDecisionStatus decisionStatus;
    private String decisionComment;
    private ReviewOperatorId decidedBy;
    private Instant decidedAt;

    private ReviewChangeUnit(ReviewChangeUnitId id,
                             int sequenceNo,
                             ReviewChangeUnitKind kind,
                             ReviewRiskLevel riskLevel,
                             String title,
                             String reason,
                             String decisionQuestion,
                             String moduleName,
                             List<String> filePaths,
                             List<ReviewHunk> keyHunks,
                             boolean requiresView,
                             Instant viewedAt,
                             ReviewUnitDecisionStatus decisionStatus,
                             String decisionComment,
                             ReviewOperatorId decidedBy,
                             Instant decidedAt) {
        if (sequenceNo < 1) {
            throw new DomainException("变更单元序号必须从 1 开始");
        }
        this.id = id;
        this.sequenceNo = sequenceNo;
        this.kind = requireNotNull(kind, "变更单元类别");
        this.riskLevel = requireNotNull(riskLevel, "变更单元风险");
        this.title = requireText(title, "变更单元标题");
        this.reason = requireText(reason, "变更原因");
        this.decisionQuestion = decisionQuestion == null || decisionQuestion.isBlank()
                ? defaultDecisionQuestion(riskLevel)
                : decisionQuestion.strip();
        this.moduleName = blankToNull(moduleName);
        this.filePaths = normalizeFiles(filePaths);
        this.keyHunks = keyHunks == null ? List.of() : List.copyOf(keyHunks);
        requireHunksBelongToUnit(this.filePaths, this.keyHunks);
        this.requiresView = requiresView
                || (riskLevel == ReviewRiskLevel.HIGH && kind.isPrimaryReviewUnit());
        this.viewedAt = viewedAt;
        this.decisionStatus = decisionStatus == null
                ? ReviewUnitDecisionStatus.PENDING
                : decisionStatus;
        this.decisionComment = blankToNull(decisionComment);
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        if (this.decisionStatus == ReviewUnitDecisionStatus.PENDING
                && (this.decidedBy != null || this.decidedAt != null)) {
            throw new DomainException("未决策的审阅项不能带决策人和决策时间");
        }
        if (this.decisionStatus != ReviewUnitDecisionStatus.PENDING
                && (this.decidedBy == null || this.decidedAt == null)) {
            throw new DomainException("已决策的审阅项必须带决策人和决策时间");
        }
    }

    public static ReviewChangeUnit create(int sequenceNo,
                                          ReviewChangeUnitKind kind,
                                          ReviewRiskLevel riskLevel,
                                          String title,
                                          String reason,
                                          String moduleName,
                                          List<String> filePaths,
                                          List<ReviewHunk> keyHunks,
                                          boolean requiresView) {
        return new ReviewChangeUnit(null, sequenceNo, kind, riskLevel, title, reason,
                defaultDecisionQuestion(riskLevel), moduleName, filePaths, keyHunks,
                requiresView, null, ReviewUnitDecisionStatus.PENDING, null, null, null);
    }

    public static ReviewChangeUnit create(int sequenceNo,
                                          ReviewChangeUnitKind kind,
                                          ReviewRiskLevel riskLevel,
                                          String title,
                                          String reason,
                                          String decisionQuestion,
                                          String moduleName,
                                          List<String> filePaths,
                                          List<ReviewHunk> keyHunks,
                                          boolean requiresView) {
        return new ReviewChangeUnit(null, sequenceNo, kind, riskLevel, title, reason,
                decisionQuestion, moduleName, filePaths, keyHunks, requiresView, null,
                ReviewUnitDecisionStatus.PENDING, null, null, null);
    }

    public static ReviewChangeUnit restore(ReviewChangeUnitId id,
                                           int sequenceNo,
                                           ReviewChangeUnitKind kind,
                                           ReviewRiskLevel riskLevel,
                                           String title,
                                           String reason,
                                           String decisionQuestion,
                                           String moduleName,
                                           List<String> filePaths,
                                           List<ReviewHunk> keyHunks,
                                           boolean requiresView,
                                           Instant viewedAt,
                                           ReviewUnitDecisionStatus decisionStatus,
                                           String decisionComment,
                                           ReviewOperatorId decidedBy,
                                           Instant decidedAt) {
        return new ReviewChangeUnit(requireNotNull(id, "变更单元 ID"), sequenceNo, kind, riskLevel,
                title, reason, decisionQuestion, moduleName, filePaths, keyHunks,
                requiresView, viewedAt, decisionStatus, decisionComment, decidedBy, decidedAt);
    }

    /** 重复查看幂等；审计只保留第一次标记时间。 */
    public boolean markViewed(Instant at) {
        if (viewedAt != null) {
            return false;
        }
        viewedAt = requireNotNull(at, "查看时间");
        return true;
    }

    /** 每个审阅项只接受一次最终人工决策，避免当前状态反复覆盖。 */
    public void decide(ReviewUnitDecisionStatus next,
                       ReviewOperatorId operatorId,
                       String comment,
                       Instant now) {
        if (decisionStatus != ReviewUnitDecisionStatus.PENDING) {
            throw new DomainException("该 diff 已完成决策，不能重复修改：" + title);
        }
        ReviewUnitDecisionStatus target = requireNotNull(next, "审阅项决策");
        String normalized = blankToNull(comment);
        if (target != ReviewUnitDecisionStatus.APPROVED && normalized == null) {
            throw new DomainException("要求修改或驳回必须填写原因");
        }
        if (normalized != null && normalized.length() > ReviewSnapshot.MAX_COMMENT_LENGTH) {
            throw new DomainException("审阅项意见超长（上限 "
                    + ReviewSnapshot.MAX_COMMENT_LENGTH + " 字符）");
        }
        decisionStatus = target;
        decisionComment = normalized;
        decidedBy = requireNotNull(operatorId, "审阅操作者");
        decidedAt = requireNotNull(now, "审阅决策时间");
        if (viewedAt == null) {
            viewedAt = decidedAt;
        }
    }

    public void assignIdentity(ReviewChangeUnitId assigned) {
        if (id != null) {
            throw new DomainException("变更单元已有 ID，不能重复分配：" + id);
        }
        id = requireNotNull(assigned, "变更单元 ID");
    }

    public boolean isRequiredAndUnviewed() {
        return requiresView && viewedAt == null;
    }

    public boolean isRequiredAndUnapproved() {
        return requiresView && decisionStatus != ReviewUnitDecisionStatus.APPROVED;
    }

    public boolean isChangeRequestedOrRejected() {
        return decisionStatus == ReviewUnitDecisionStatus.CHANGES_REQUESTED
                || decisionStatus == ReviewUnitDecisionStatus.REJECTED;
    }

    public ReviewChangeUnitId getId() {
        return id;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public ReviewChangeUnitKind getKind() {
        return kind;
    }

    public ReviewRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public String getTitle() {
        return title;
    }

    public String getReason() {
        return reason;
    }

    public String getDecisionQuestion() {
        return decisionQuestion;
    }

    public String getModuleName() {
        return moduleName;
    }

    public List<String> getFilePaths() {
        return filePaths;
    }

    public List<ReviewHunk> getKeyHunks() {
        return keyHunks;
    }

    public boolean isRequiresView() {
        return requiresView;
    }

    public Instant getViewedAt() {
        return viewedAt;
    }

    public ReviewUnitDecisionStatus getDecisionStatus() {
        return decisionStatus;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public ReviewOperatorId getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public static String defaultDecisionQuestion(ReviewRiskLevel risk) {
        return switch (risk) {
            case HIGH -> "确认这项行为变化是否符合任务目标，并判断是否引入安全、数据或回归风险。";
            case MEDIUM -> "确认这项改动是否必要，并判断是否改变未声明的现有行为。";
            case LOW -> "确认这项低风险改动可以随本次交付合并。";
        };
    }

    private static List<String> normalizeFiles(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            throw new DomainException("变更单元至少包含一个文件");
        }
        List<String> normalized = paths.stream()
                .filter(path -> path != null && !path.isBlank())
                .map(path -> path.replace('\\', '/').strip())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new DomainException("变更单元至少包含一个有效文件路径");
        }
        for (String path : normalized) {
            if (path.startsWith("/") || path.contains("../")) {
                throw new DomainException("变更单元文件必须位于项目内：" + path);
            }
        }
        return normalized;
    }

    private static void requireHunksBelongToUnit(List<String> files, List<ReviewHunk> hunks) {
        for (ReviewHunk hunk : hunks) {
            if (!files.contains(hunk.filePath())) {
                throw new DomainException("hunk 文件不属于该变更单元：" + hunk.filePath());
            }
        }
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
        return value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
