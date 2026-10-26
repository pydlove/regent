package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 一次审阅固定的代码事实与验收状态。
 *
 * <p>任务状态机不新增“已审阅”：任务可以先正常完成，审阅再旁路生成。快照自己守住
 * 通过条件，所有状态迁移都走领域方法，仓储只负责持久化投影。
 */
public class ReviewSnapshot {

    public static final int MAX_DIFF_CONTENT_LENGTH = 2_000_000;
    public static final int MAX_COMMENT_LENGTH = 4_000;

    private ReviewSnapshotId id;
    private final ReviewTaskId taskId;
    private final ReviewTaskRunId taskRunId;
    private final ReviewProjectId projectId;
    private final String baseRef;
    private final String headRef;
    private final boolean gitRepository;
    private final String diffHash;
    private final int fileCount;
    private final int additions;
    private final int deletions;
    private final List<ReviewDiffFile> files;
    private final String diffContent;
    private final boolean diffTruncated;
    private ReviewSnapshotStatus status;
    private ReviewAnalysisStatus analysisStatus;
    private String analysisError;
    private final List<ReviewChangeUnit> changeUnits;
    private final List<ReviewFinding> findings;
    private final List<ReviewEvidence> evidence;
    private String commitSha;
    private String commitMessage;
    private ReviewOperatorId committedBy;
    private Instant committedAt;
    private Instant reviewedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private ReviewSnapshot(ReviewSnapshotId id,
                           ReviewTaskId taskId,
                           ReviewTaskRunId taskRunId,
                           ReviewProjectId projectId,
                           String baseRef,
                           String headRef,
                           boolean gitRepository,
                           String diffHash,
                           int fileCount,
                           int additions,
                           int deletions,
                           List<ReviewDiffFile> files,
                           String diffContent,
                           boolean diffTruncated,
                           ReviewSnapshotStatus status,
                           ReviewAnalysisStatus analysisStatus,
                           String analysisError,
                           List<ReviewChangeUnit> changeUnits,
                           List<ReviewFinding> findings,
                           List<ReviewEvidence> evidence,
                           String commitSha,
                           String commitMessage,
                           ReviewOperatorId committedBy,
                           Instant committedAt,
                           Instant reviewedAt,
                           Instant createdAt,
                           Instant updatedAt) {
        this.id = id;
        this.taskId = requireNotNull(taskId, "审阅任务");
        this.taskRunId = requireNotNull(taskRunId, "审阅执行记录");
        this.projectId = requireNotNull(projectId, "审阅项目");
        this.baseRef = blankToNull(baseRef);
        this.headRef = blankToNull(headRef);
        this.gitRepository = gitRepository;
        if (gitRepository && this.baseRef == null) {
            throw new DomainException("Git 项目的审阅快照必须带 base ref");
        }
        if (diffHash == null || !diffHash.matches("[0-9a-f]{64}")) {
            throw new DomainException("审阅 diff 哈希必须是 64 位小写十六进制");
        }
        if (fileCount < 0 || additions < 0 || deletions < 0) {
            throw new DomainException("审阅快照文件与行数统计不能为负数");
        }
        this.diffHash = diffHash;
        this.fileCount = fileCount;
        this.additions = additions;
        this.deletions = deletions;
        this.files = files == null ? List.of() : List.copyOf(files);
        this.diffContent = truncate(diffContent == null ? "" : diffContent, MAX_DIFF_CONTENT_LENGTH);
        this.diffTruncated = diffTruncated || this.diffContent.length() < (diffContent == null ? 0 : diffContent.length());
        this.status = requireNotNull(status, "审阅状态");
        this.analysisStatus = requireNotNull(analysisStatus, "审阅分析状态");
        this.analysisError = blankToNull(analysisError);
        this.changeUnits = new ArrayList<>(changeUnits == null ? List.of() : changeUnits);
        this.findings = new ArrayList<>(findings == null ? List.of() : findings);
        this.evidence = new ArrayList<>(evidence == null ? List.of() : evidence);
        this.commitSha = blankToNull(commitSha);
        this.commitMessage = blankToNull(commitMessage);
        this.committedBy = committedBy;
        this.committedAt = committedAt;
        if (status == ReviewSnapshotStatus.COMMITTED
                && (this.commitSha == null || this.committedBy == null || committedAt == null)) {
            throw new DomainException("已提交快照必须包含提交 SHA、提交人和提交时间");
        }
        validateReferences();
        this.reviewedAt = reviewedAt;
        this.createdAt = requireNotNull(createdAt, "审阅创建时间");
        this.updatedAt = requireNotNull(updatedAt, "审阅更新时间");
    }

    public static ReviewSnapshot create(ReviewTaskId taskId,
                                        ReviewTaskRunId taskRunId,
                                        ReviewProjectId projectId,
                                        ReviewDiff diff,
                                        Instant now) {
        requireNotNull(diff, "审阅代码事实");
        Instant at = requireNotNull(now, "审阅创建时间");
        return new ReviewSnapshot(null, taskId, taskRunId, projectId,
                diff.baseRef(), diff.headRef(), diff.gitRepository(), diff.diffHash(),
                diff.fileCount(), diff.additions(), diff.deletions(),
                diff.files(), diff.content(), diff.truncated(), ReviewSnapshotStatus.WAITING_REVIEW,
                ReviewAnalysisStatus.PENDING, null, List.of(), List.of(), List.of(),
                null, null, null, null, null, at, at);
    }

    public static ReviewSnapshot restore(ReviewSnapshotState state) {
        requireNotNull(state, "审阅快照状态");
        return new ReviewSnapshot(state.id(), state.taskId(), state.taskRunId(), state.projectId(),
                state.baseRef(), state.headRef(), state.gitRepository(), state.diffHash(),
                state.fileCount(), state.additions(), state.deletions(), state.files(),
                state.diffContent(), state.diffTruncated(), state.status(), state.analysisStatus(),
                state.analysisError(), state.changeUnits(), state.findings(), state.evidence(),
                state.commitSha(), state.commitMessage(), state.committedBy(), state.committedAt(),
                state.reviewedAt(), state.createdAt(), state.updatedAt());
    }

    /** 预审只补充解释与分组；代码事实已在创建快照时固定，不能被分析结果改写。 */
    public void attachAnalysis(ReviewAnalysis analysis, Instant now) {
        requireNotNull(analysis, "审阅分析");
        if (analysisStatus == ReviewAnalysisStatus.SUCCEEDED) {
            return;
        }
        changeUnits.clear();
        findings.clear();
        evidence.clear();
        changeUnits.addAll(analysis.changeUnits());
        findings.addAll(analysis.findings());
        evidence.addAll(analysis.evidence());
        changeUnits.sort(Comparator.comparingInt(ReviewChangeUnit::getSequenceNo));
        validateReferences();
        analysisStatus = ReviewAnalysisStatus.SUCCEEDED;
        analysisError = null;
        touch(now);
    }

    public void failAnalysis(String reason, Instant now) {
        analysisStatus = ReviewAnalysisStatus.FAILED;
        analysisError = requireText(reason, "审阅分析失败原因");
        touch(now);
    }

    public void beginReview(Instant now) {
        if (status == ReviewSnapshotStatus.WAITING_REVIEW) {
            status = ReviewSnapshotStatus.IN_REVIEW;
            touch(now);
        }
    }

    /** 返回 false 表示已经查看过，调用方无需重复写库。 */
    public boolean markViewed(ReviewChangeUnitId unitId, Instant now) {
        if (!status.isOpen()) {
            return false;
        }
        ReviewChangeUnit unit = findUnit(unitId);
        if (!unit.markViewed(now)) {
            return false;
        }
        beginReview(now);
        touch(now);
        return true;
    }

    public void markFinding(ReviewFindingId findingId, ReviewFindingStatus next, Instant now) {
        requireOpen("只有待审阅或审阅中的快照可以更新发现");
        ReviewFinding finding = findFinding(findingId);
        finding.markStatus(next, now);
        beginReview(now);
        touch(now);
    }

    /** 单独确认一个 diff 审阅项；逐项决策完成后才能通过整单。 */
    public void decideUnit(ReviewChangeUnitId unitId,
                           ReviewUnitDecisionStatus decision,
                           ReviewOperatorId operatorId,
                           String comment,
                           Instant now) {
        requireOpen("只有待审阅或审阅中的快照可以决策 diff");
        findUnit(unitId).decide(decision, operatorId, comment, now);
        beginReview(now);
        touch(now);
    }

    /** 合并后到的测试证据；同一 sourceType + sourceId 只保留一行最新状态。 */
    public boolean refreshEvidence(List<ReviewEvidence> refreshed, Instant now) {
        if (!status.isOpen() || refreshed == null || refreshed.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (ReviewEvidence incoming : refreshed) {
            ReviewEvidence existing = evidence.stream()
                    .filter(item -> sameSource(item, incoming))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                evidence.add(incoming);
                changed = true;
            } else {
                changed |= existing.refresh(incoming.getStatus(), incoming.getSummary(),
                        incoming.getOccurredAt());
            }
        }
        if (changed) {
            touch(now);
        }
        return changed;
    }

    public void approve(Instant now) {
        requireOpen("只有待审阅或审阅中的快照可以通过");
        List<String> required = changeUnits.stream()
                .filter(ReviewChangeUnit::isRequiredAndUnapproved)
                .map(ReviewChangeUnit::getTitle)
                .toList();
        if (!required.isEmpty()) {
            throw new DomainException("高风险必看项尚未逐项通过：" + String.join("、", required));
        }
        List<String> blocked = changeUnits.stream()
                .filter(ReviewChangeUnit::isChangeRequestedOrRejected)
                .map(ReviewChangeUnit::getTitle)
                .toList();
        if (!blocked.isEmpty()) {
            throw new DomainException("仍有 diff 被要求修改或驳回：" + String.join("、", blocked));
        }
        List<String> blockers = findings.stream()
                .filter(ReviewFinding::isUnresolvedBlocker)
                .map(ReviewFinding::getTitle)
                .toList();
        if (!blockers.isEmpty()) {
            throw new DomainException("仍有未处理阻断问题：" + String.join("、", blockers));
        }
        status = ReviewSnapshotStatus.APPROVED;
        reviewedAt = requireNotNull(now, "审阅结论时间");
        touch(now);
    }

    public void requestChanges(Instant now) {
        requireOpen("只有待审阅或审阅中的快照可以要求修改");
        status = ReviewSnapshotStatus.CHANGES_REQUESTED;
        reviewedAt = requireNotNull(now, "审阅结论时间");
        touch(now);
    }

    public void reject(Instant now) {
        requireOpen("只有待审阅或审阅中的快照可以驳回");
        status = ReviewSnapshotStatus.REJECTED;
        reviewedAt = requireNotNull(now, "审阅结论时间");
        touch(now);
    }

    /** 人工通过后由平台按审阅文件清单创建提交，提交事实与快照绑定且不可重复写入。 */
    public void markCommitted(ReviewOperatorId operatorId,
                              String commitSha,
                              String commitMessage,
                              Instant now) {
        if (status != ReviewSnapshotStatus.APPROVED) {
            throw new DomainException("只有已通过且尚未提交的审阅快照可以提交 Git");
        }
        if (!gitRepository) {
            throw new DomainException("当前项目不是可提交的 Git 工作区");
        }
        String normalizedSha = requireText(commitSha, "Git 提交 SHA");
        if (!normalizedSha.matches("[0-9a-f]{40,64}")) {
            throw new DomainException("Git 提交 SHA 格式不合法");
        }
        this.status = ReviewSnapshotStatus.COMMITTED;
        this.committedBy = requireNotNull(operatorId, "提交人");
        this.commitSha = normalizedSha;
        this.commitMessage = requireText(commitMessage, "Git 提交信息");
        this.committedAt = requireNotNull(now, "提交时间");
        touch(now);
    }

    /** 代码再次变化后旧事实保留，但结论立即失去部署效力。 */
    public void expire(Instant now) {
        if (status == ReviewSnapshotStatus.EXPIRED) {
            return;
        }
        status = ReviewSnapshotStatus.EXPIRED;
        touch(now);
    }

    public boolean matchesDiffHash(String currentDiffHash) {
        return diffHash.equals(currentDiffHash);
    }

    public boolean isDeployable() {
        return status == ReviewSnapshotStatus.APPROVED
                || status == ReviewSnapshotStatus.COMMITTED;
    }

    public boolean canApprove() {
        return status.isOpen()
                && unresolvedBlockerCount() == 0
                && changeUnits.stream().noneMatch(ReviewChangeUnit::isRequiredAndUnapproved)
                && changeUnits.stream().noneMatch(ReviewChangeUnit::isChangeRequestedOrRejected);
    }

    public List<String> reviewedFilePaths() {
        if (!files.isEmpty()) {
            return files.stream().map(ReviewDiffFile::path).sorted().toList();
        }
        return changeUnits.stream()
                .flatMap(unit -> unit.getFilePaths().stream())
                .distinct()
                .sorted()
                .toList();
    }

    public int requiredUnitCount() {
        return (int) changeUnits.stream().filter(ReviewChangeUnit::isRequiresView).count();
    }

    public int viewedRequiredUnitCount() {
        return (int) changeUnits.stream()
                .filter(ReviewChangeUnit::isRequiresView)
                .filter(unit -> unit.getViewedAt() != null)
                .count();
    }

    public int approvedUnitCount() {
        return (int) changeUnits.stream()
                .filter(unit -> unit.getDecisionStatus() == ReviewUnitDecisionStatus.APPROVED)
                .count();
    }

    public int changeRequestedUnitCount() {
        return (int) changeUnits.stream()
                .filter(unit -> unit.getDecisionStatus()
                        == ReviewUnitDecisionStatus.CHANGES_REQUESTED)
                .count();
    }

    public int rejectedUnitCount() {
        return (int) changeUnits.stream()
                .filter(unit -> unit.getDecisionStatus() == ReviewUnitDecisionStatus.REJECTED)
                .count();
    }

    public long unresolvedBlockerCount() {
        return findings.stream().filter(ReviewFinding::isUnresolvedBlocker).count();
    }

    public void assignIdentity(ReviewSnapshotId assigned) {
        if (id != null) {
            throw new DomainException("审阅快照已有 ID，不能重复分配：" + id);
        }
        id = requireNotNull(assigned, "审阅快照 ID");
    }

    private ReviewChangeUnit findUnit(ReviewChangeUnitId unitId) {
        requireNotNull(unitId, "变更单元 ID");
        return changeUnits.stream()
                .filter(unit -> unitId.equals(unit.getId()))
                .findFirst()
                .orElseThrow(() -> new DomainException("审阅快照中不存在该变更单元：" + unitId.value()));
    }

    private ReviewFinding findFinding(ReviewFindingId findingId) {
        requireNotNull(findingId, "发现 ID");
        return findings.stream()
                .filter(finding -> findingId.equals(finding.getId()))
                .findFirst()
                .orElseThrow(() -> new DomainException("审阅快照中不存在该发现：" + findingId.value()));
    }

    private void requireOpen(String message) {
        if (!status.isOpen()) {
            throw new DomainException(message + "，当前状态：" + status);
        }
    }

    private void validateReferences() {
        List<ReviewChangeUnitId> unitIds = changeUnits.stream()
                .map(ReviewChangeUnit::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        for (ReviewFinding finding : findings) {
            if (finding.getChangeUnitId() != null
                    && !unitIds.isEmpty()
                    && !unitIds.contains(finding.getChangeUnitId())) {
                throw new DomainException("发现关联了不属于本快照的变更单元：" + finding.getChangeUnitId().value());
            }
        }
        for (ReviewEvidence item : evidence) {
            if (item.getChangeUnitId() != null
                    && !unitIds.isEmpty()
                    && !unitIds.contains(item.getChangeUnitId())) {
                throw new DomainException("证据关联了不属于本快照的变更单元：" + item.getChangeUnitId().value());
            }
        }
        List<Integer> sequences = changeUnits.stream().map(ReviewChangeUnit::getSequenceNo).toList();
        if (sequences.stream().distinct().count() != sequences.size()) {
            throw new DomainException("同一快照的变更单元序号不能重复");
        }
    }

    private static boolean sameSource(ReviewEvidence left, ReviewEvidence right) {
        return left.getSourceType().equals(right.getSourceType())
                && java.util.Objects.equals(left.getSourceId(), right.getSourceId());
    }

    private void touch(Instant now) {
        updatedAt = requireNotNull(now, "审阅更新时间");
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "\n…（diff 已截断）";
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

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public ReviewSnapshotId getId() {
        return id;
    }

    public ReviewTaskId getTaskId() {
        return taskId;
    }

    public ReviewTaskRunId getTaskRunId() {
        return taskRunId;
    }

    public ReviewProjectId getProjectId() {
        return projectId;
    }

    public String getBaseRef() {
        return baseRef;
    }

    public String getHeadRef() {
        return headRef;
    }

    public boolean isGitRepository() {
        return gitRepository;
    }

    public String getDiffHash() {
        return diffHash;
    }

    public List<ReviewDiffFile> getFiles() {
        return files;
    }

    public int getFileCount() {
        return fileCount;
    }

    public int getAdditions() {
        return additions;
    }

    public int getDeletions() {
        return deletions;
    }

    public String getDiffContent() {
        return diffContent;
    }

    public boolean isDiffTruncated() {
        return diffTruncated;
    }

    public ReviewSnapshotStatus getStatus() {
        return status;
    }

    public ReviewAnalysisStatus getAnalysisStatus() {
        return analysisStatus;
    }

    public String getAnalysisError() {
        return analysisError;
    }

    public List<ReviewChangeUnit> getChangeUnits() {
        return List.copyOf(changeUnits);
    }

    public List<ReviewFinding> getFindings() {
        return List.copyOf(findings);
    }

    public List<ReviewEvidence> getEvidence() {
        return List.copyOf(evidence);
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public ReviewOperatorId getCommittedBy() {
        return committedBy;
    }

    public Instant getCommittedAt() {
        return committedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
