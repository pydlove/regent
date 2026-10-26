package com.pydlove.regent.requirement.domain.model;

import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** 需求聚合：保存原始描述、澄清结果和待确认的任务草稿。 */
public class Requirement {

    private static final int MAX_TASKS = 12;
    private static final int MAX_TITLE = 256;

    private RequirementId id;
    private final ProjectId projectId;
    private final InitiativeId initiativeId;
    private ProductVersionId versionId;
    private String title;
    private String rawText;
    private String acceptanceText;
    private String clarificationText;
    private RequirementStatus status;
    private RequirementPoolStatus poolStatus;
    private final RequirementSource source;
    private String reviewComment;
    private UserId reviewedBy;
    private Instant reviewedAt;
    private List<String> clarificationQuestions;
    private List<String> assumptions;
    private List<RequirementTaskDraft> taskDrafts;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant plannedAt;

    private Requirement(RequirementId id,
                        ProjectId projectId,
                        InitiativeId initiativeId,
                        ProductVersionId versionId,
                        String title,
                        String rawText,
                        String acceptanceText,
                        String clarificationText,
                        RequirementStatus status,
                        RequirementPoolStatus poolStatus,
                        RequirementSource source,
                        String reviewComment,
                        UserId reviewedBy,
                        Instant reviewedAt,
                        List<String> clarificationQuestions,
                        List<String> assumptions,
                        List<RequirementTaskDraft> taskDrafts,
                        Instant createdAt,
                        Instant updatedAt,
                        Instant plannedAt) {
        this.id = id;
        this.projectId = requireNotNull(projectId, "所属项目");
        this.initiativeId = initiativeId;
        this.versionId = versionId;
        this.poolStatus = requireNotNull(poolStatus, "需求池状态");
        this.source = requireNotNull(source, "需求来源");
        this.reviewComment = blankToNull(reviewComment);
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.createdAt = requireNotNull(createdAt, "创建时间").truncatedTo(ChronoUnit.MILLIS);
        this.updatedAt = requireNotNull(updatedAt, "更新时间").truncatedTo(ChronoUnit.MILLIS);
        this.plannedAt = plannedAt;
        apply(title, rawText, acceptanceText, clarificationText, status,
                clarificationQuestions, assumptions, taskDrafts);
    }

    public static Requirement create(ProjectId projectId,
                                     String title,
                                     String rawText,
                                     String acceptanceText,
                                     String clarificationText) {
        return create(projectId, null, title, rawText, acceptanceText, clarificationText);
    }

    public static Requirement create(ProjectId projectId,
                                     InitiativeId initiativeId,
                                     String title,
                                     String rawText,
                                     String acceptanceText,
                                     String clarificationText) {
        return create(projectId, initiativeId, title, rawText, acceptanceText,
                clarificationText, RequirementSource.MANUAL);
    }

    public static Requirement create(ProjectId projectId,
                                     InitiativeId initiativeId,
                                     String title,
                                     String rawText,
                                     String acceptanceText,
                                     String clarificationText,
                                     RequirementSource source) {
        Instant now = now();
        return new Requirement(null, projectId, initiativeId, null, title, rawText,
                acceptanceText, clarificationText,
                RequirementStatus.DRAFT, RequirementPoolStatus.POOL,
                source, null, null, null,
                List.of(), List.of(), List.of(), now, now, null);
    }

    public static Requirement restore(RequirementId id,
                                      ProjectId projectId,
                                      String title,
                                      String rawText,
                                      String acceptanceText,
                                      String clarificationText,
                                      RequirementStatus status,
                                      List<String> clarificationQuestions,
                                      List<String> assumptions,
                                      List<RequirementTaskDraft> taskDrafts,
                                      Instant createdAt,
                                      Instant updatedAt,
                                      Instant plannedAt) {
        return restore(id, projectId, null, null, title, rawText, acceptanceText, clarificationText,
                status, RequirementPoolStatus.POOL, RequirementSource.MANUAL,
                null, null, null, clarificationQuestions, assumptions, taskDrafts,
                createdAt, updatedAt, plannedAt);
    }

    public static Requirement restore(RequirementId id,
                                      ProjectId projectId,
                                      InitiativeId initiativeId,
                                      String title,
                                      String rawText,
                                      String acceptanceText,
                                      String clarificationText,
                                      RequirementStatus status,
                                      List<String> clarificationQuestions,
                                      List<String> assumptions,
                                      List<RequirementTaskDraft> taskDrafts,
                                      Instant createdAt,
                                      Instant updatedAt,
                                      Instant plannedAt) {
        return restore(id, projectId, initiativeId, null, title, rawText, acceptanceText,
                clarificationText, status, RequirementPoolStatus.POOL,
                RequirementSource.MANUAL, null, null, null,
                clarificationQuestions, assumptions, taskDrafts,
                createdAt, updatedAt, plannedAt);
    }

    public static Requirement restore(RequirementId id,
                                      ProjectId projectId,
                                      InitiativeId initiativeId,
                                      ProductVersionId versionId,
                                      String title,
                                      String rawText,
                                      String acceptanceText,
                                      String clarificationText,
                                      RequirementStatus status,
                                      RequirementPoolStatus poolStatus,
                                      RequirementSource source,
                                      String reviewComment,
                                      UserId reviewedBy,
                                      Instant reviewedAt,
                                      List<String> clarificationQuestions,
                                      List<String> assumptions,
                                      List<RequirementTaskDraft> taskDrafts,
                                      Instant createdAt,
                                      Instant updatedAt,
                                      Instant plannedAt) {
        return new Requirement(id, projectId, initiativeId, versionId, title, rawText,
                acceptanceText, clarificationText, status, poolStatus, source,
                reviewComment, reviewedBy, reviewedAt,
                clarificationQuestions, assumptions, taskDrafts,
                createdAt, updatedAt, plannedAt);
    }

    public void updateDraft(String title,
                            String rawText,
                            String acceptanceText,
                            String clarificationText) {
        if (poolStatus == RequirementPoolStatus.IN_REVIEW
                || poolStatus == RequirementPoolStatus.FORMAL) {
            throw new DomainException("需求评审中或已成为正式需求，不能修改原始描述");
        }
        if (status == RequirementStatus.PLANNED
                || status == RequirementStatus.RUNNING
                || status == RequirementStatus.PARTIAL
                || status == RequirementStatus.DONE) {
            throw new DomainException("需求已经确认，不能直接修改原始描述");
        }
        apply(title, rawText, acceptanceText, clarificationText, RequirementStatus.DRAFT,
                List.of(), List.of(), List.of());
        touch();
    }

    public void assignVersion(ProductVersionId versionId) {
        if (poolStatus == RequirementPoolStatus.IN_REVIEW
                || poolStatus == RequirementPoolStatus.FORMAL) {
            throw new DomainException("需求评审中或已成为正式需求，不能更换产品版本");
        }
        this.versionId = requireNotNull(versionId, "产品版本");
        if (poolStatus == RequirementPoolStatus.REJECTED) {
            poolStatus = RequirementPoolStatus.POOL;
        }
        touch();
    }

    public void removeFromVersion() {
        if (poolStatus == RequirementPoolStatus.IN_REVIEW
                || poolStatus == RequirementPoolStatus.FORMAL) {
            throw new DomainException("需求评审中或已成为正式需求，不能移出产品版本");
        }
        this.versionId = null;
        poolStatus = RequirementPoolStatus.POOL;
        touch();
    }

    public void submitPoolReview() {
        if (versionId == null) {
            throw new DomainException("需求加入产品版本后才能提交评审");
        }
        if (poolStatus != RequirementPoolStatus.POOL
                && poolStatus != RequirementPoolStatus.REJECTED) {
            throw new DomainException("当前需求不能提交评审：" + poolStatus);
        }
        poolStatus = RequirementPoolStatus.IN_REVIEW;
        reviewComment = null;
        reviewedBy = null;
        reviewedAt = null;
        touch();
    }

    public void approvePoolReview(UserId reviewer, String comment) {
        requirePoolReview();
        poolStatus = RequirementPoolStatus.FORMAL;
        reviewComment = blankToNull(comment);
        reviewedBy = requireNotNull(reviewer, "评审人");
        reviewedAt = now();
        touch();
    }

    public void rejectPoolReview(UserId reviewer, String comment) {
        requirePoolReview();
        String reason = requireText(comment, "驳回原因");
        poolStatus = RequirementPoolStatus.REJECTED;
        reviewComment = reason;
        reviewedBy = requireNotNull(reviewer, "评审人");
        reviewedAt = now();
        touch();
    }

    private void requirePoolReview() {
        if (poolStatus != RequirementPoolStatus.IN_REVIEW) {
            throw new DomainException("只有评审中的需求可以记录评审结果");
        }
    }

    public void applyAnalysis(List<String> questions,
                              List<String> assumptions,
                              List<RequirementTaskDraft> drafts) {
        if (status == RequirementStatus.PLANNED || status == RequirementStatus.DONE) {
            throw new DomainException("需求已经确认，不能重新拆解");
        }
        this.clarificationQuestions = normalize(questions);
        this.assumptions = normalize(assumptions);
        List<RequirementTaskDraft> values = drafts == null ? List.of() : List.copyOf(drafts);
        this.taskDrafts = values.isEmpty() ? values : List.copyOf(requireTaskDrafts(values));
        this.status = this.taskDrafts.isEmpty()
                ? RequirementStatus.CLARIFYING
                : RequirementStatus.READY;
        touch();
    }

    public void replaceTaskDrafts(Collection<RequirementTaskDraft> drafts) {
        if (status == RequirementStatus.PLANNED || status == RequirementStatus.DONE) {
            throw new DomainException("需求已经确认，不能再修改任务草稿");
        }
        this.taskDrafts = List.copyOf(requireTaskDrafts(drafts));
        this.status = RequirementStatus.READY;
        touch();
    }

    public void confirm() {
        if (status != RequirementStatus.READY) {
            throw new DomainException("需求尚未完成拆解，不能确认创建任务");
        }
        requireTaskDrafts(taskDrafts);
        this.status = RequirementStatus.PLANNED;
        this.plannedAt = now();
        touch();
    }

    public void cancel() {
        if (status == RequirementStatus.DONE) {
            throw new DomainException("已完成需求不能取消");
        }
        this.status = RequirementStatus.CANCELLED;
        touch();
    }

    /**
     * 只有尚未进入评审、也没有开始研发的需求可以删除。
     * 正式需求关联了交付链路，直接删除会让研发记录失去来源。
     */
    public void requireTrashable() {
        if (poolStatus == RequirementPoolStatus.IN_REVIEW
                || poolStatus == RequirementPoolStatus.FORMAL) {
            throw new DomainException("评审中或已正式化的需求不能删除");
        }
        if (status == RequirementStatus.PLANNED
                || status == RequirementStatus.RUNNING
                || status == RequirementStatus.PARTIAL
                || status == RequirementStatus.DONE) {
            throw new DomainException("已经进入研发的需求不能删除");
        }
    }

    public void assignIdentity(RequirementId assigned) {
        if (id != null) {
            throw new DomainException("需求已有 ID，不能重复分配");
        }
        this.id = requireNotNull(assigned, "需求 ID");
    }

    private void apply(String title,
                       String rawText,
                       String acceptanceText,
                       String clarificationText,
                       RequirementStatus status,
                       List<String> questions,
                       List<String> assumptions,
                       List<RequirementTaskDraft> drafts) {
        String normalizedTitle = requireText(title, "需求标题");
        if (normalizedTitle.length() > MAX_TITLE) {
            throw new DomainException("需求标题不能超过 256 个字符");
        }
        this.title = normalizedTitle;
        this.rawText = requireText(rawText, "需求描述");
        this.acceptanceText = blankToNull(acceptanceText);
        this.clarificationText = blankToNull(clarificationText);
        this.status = requireNotNull(status, "需求状态");
        this.clarificationQuestions = normalize(questions);
        this.assumptions = normalize(assumptions);
        this.taskDrafts = List.copyOf(drafts == null ? List.of() : drafts);
    }

    private static List<RequirementTaskDraft> requireTaskDrafts(
            Collection<RequirementTaskDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            throw new DomainException("请至少保留一个可执行任务");
        }
        if (drafts.size() > MAX_TASKS) {
            throw new DomainException("一次需求最多拆解 12 个任务");
        }
        List<RequirementTaskDraft> values = List.copyOf(drafts);
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (RequirementTaskDraft draft : values) {
            if (!keys.add(draft.key())) {
                throw new DomainException("任务草稿编号重复：" + draft.key());
            }
        }
        for (RequirementTaskDraft draft : values) {
            for (String dependency : draft.dependencies()) {
                if (!keys.contains(dependency)) {
                    throw new DomainException("任务依赖不存在：" + dependency);
                }
            }
        }
        requireAcyclic(values);
        return values;
    }

    private static void requireAcyclic(List<RequirementTaskDraft> drafts) {
        java.util.Map<String, List<String>> graph = drafts.stream().collect(
                java.util.stream.Collectors.toMap(
                        RequirementTaskDraft::key,
                        RequirementTaskDraft::dependencies));
        java.util.Set<String> visiting = new java.util.HashSet<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        for (String key : graph.keySet()) {
            visit(key, graph, visiting, visited);
        }
    }

    private static void visit(String key,
                              java.util.Map<String, List<String>> graph,
                              java.util.Set<String> visiting,
                              java.util.Set<String> visited) {
        if (visited.contains(key)) return;
        if (!visiting.add(key)) {
            throw new DomainException("任务依赖不能形成循环：" + key);
        }
        for (String dependency : graph.getOrDefault(key, List.of())) {
            visit(dependency, graph, visiting, visited);
        }
        visiting.remove(key);
        visited.add(key);
    }

    private static List<String> normalize(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) throw new DomainException(field + "不能为空");
        return value;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private void touch() {
        this.updatedAt = now();
    }

    public RequirementId getId() {
        return id;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public InitiativeId getInitiativeId() {
        return initiativeId;
    }

    public ProductVersionId getVersionId() {
        return versionId;
    }

    public String getTitle() {
        return title;
    }

    public String getRawText() {
        return rawText;
    }

    public String getAcceptanceText() {
        return acceptanceText;
    }

    public String getClarificationText() {
        return clarificationText;
    }

    public RequirementStatus getStatus() {
        return status;
    }

    public RequirementPoolStatus getPoolStatus() {
        return poolStatus;
    }

    public RequirementSource getSource() {
        return source;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public UserId getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public List<String> getClarificationQuestions() {
        return clarificationQuestions;
    }

    public List<String> getAssumptions() {
        return assumptions;
    }

    public List<RequirementTaskDraft> getTaskDrafts() {
        return taskDrafts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getPlannedAt() {
        return plannedAt;
    }
}
