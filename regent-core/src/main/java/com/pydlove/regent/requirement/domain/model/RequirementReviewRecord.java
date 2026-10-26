package com.pydlove.regent.requirement.domain.model;

import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 一次线下需求评审的线上留痕。 */
public class RequirementReviewRecord {

    private RequirementReviewRecordId id;
    private final ProductVersionId versionId;
    private final int reviewNo;
    private final LocalDate reviewDate;
    private final String participants;
    private String conclusion;
    private final String minutes;
    private RequirementReviewStatus status;
    private String reviewComment;
    private final List<RequirementReviewCandidate> requirements;
    private final UserId submittedBy;
    private UserId decidedBy;
    private Instant decidedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private RequirementReviewRecord(RequirementReviewRecordId id,
                                    ProductVersionId versionId,
                                    int reviewNo,
                                    LocalDate reviewDate,
                                    String participants,
                                    String conclusion,
                                    String minutes,
                                    RequirementReviewStatus status,
                                    String reviewComment,
                                    List<RequirementReviewCandidate> requirements,
                                    UserId submittedBy,
                                    UserId decidedBy,
                                    Instant decidedAt,
                                    Instant createdAt,
                                    Instant updatedAt) {
        this.id = id;
        this.versionId = requireNotNull(versionId, "产品版本");
        if (reviewNo <= 0) {
            throw new DomainException("需求评审次数必须大于 0");
        }
        this.reviewNo = reviewNo;
        this.reviewDate = requireNotNull(reviewDate, "评审日期");
        if (reviewDate.isAfter(LocalDate.now())) {
            throw new DomainException("评审日期不能晚于今天");
        }
        this.participants = optionalText(participants, "参与人", 512);
        this.conclusion = requireText(conclusion, "评审结论", 512);
        this.minutes = optionalText(minutes, "会议纪要", 200_000);
        this.status = requireNotNull(status, "审核状态");
        this.reviewComment = optionalText(reviewComment, "审核意见", 4000);
        if (requirements == null || requirements.isEmpty()) {
            throw new DomainException("当前版本没有可记录评审的需求");
        }
        this.requirements = List.copyOf(requirements);
        this.submittedBy = requireNotNull(submittedBy, "提交人");
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.createdAt = requireNotNull(createdAt, "创建时间").truncatedTo(ChronoUnit.MILLIS);
        this.updatedAt = requireNotNull(updatedAt, "更新时间").truncatedTo(ChronoUnit.MILLIS);
    }

    public static RequirementReviewRecord submit(ProductVersionId versionId,
                                                 int reviewNo,
                                                 LocalDate reviewDate,
                                                 String minutes,
                                                 List<RequirementReviewCandidate> requirements,
                                                 UserId submittedBy) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return new RequirementReviewRecord(
                null, versionId, reviewNo, reviewDate, null,
                "待审核", minutes, RequirementReviewStatus.SUBMITTED, null,
                requirements, submittedBy, null, null, now, now);
    }

    public static RequirementReviewRecord restore(RequirementReviewRecordId id,
                                                  ProductVersionId versionId,
                                                  int reviewNo,
                                                  LocalDate reviewDate,
                                                  String participants,
                                                  String conclusion,
                                                  String minutes,
                                                  RequirementReviewStatus status,
                                                  String reviewComment,
                                                  List<RequirementReviewCandidate> requirements,
                                                  UserId submittedBy,
                                                  UserId decidedBy,
                                                  Instant decidedAt,
                                                  Instant createdAt,
                                                  Instant updatedAt) {
        return new RequirementReviewRecord(
                id, versionId, reviewNo, reviewDate, participants,
                conclusion, minutes, status, reviewComment, requirements,
                submittedBy, decidedBy, decidedAt, createdAt, updatedAt);
    }

    public void approve(UserId reviewer, String comment) {
        requireSubmitted();
        status = RequirementReviewStatus.APPROVED;
        conclusion = "评审通过";
        reviewComment = optionalText(comment, "审核意见", 4000);
        decidedBy = requireNotNull(reviewer, "审核人");
        decidedAt = now();
        touch();
    }

    public void reject(UserId reviewer, String comment) {
        requireSubmitted();
        String normalizedComment = requireText(comment, "审核意见", 4000);
        status = RequirementReviewStatus.REJECTED;
        conclusion = "评审驳回";
        reviewComment = normalizedComment;
        decidedBy = requireNotNull(reviewer, "审核人");
        decidedAt = now();
        touch();
    }

    private void requireSubmitted() {
        if (status != RequirementReviewStatus.SUBMITTED) {
            throw new DomainException("该需求评审已经审核完成");
        }
    }

    private void touch() {
        updatedAt = now();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public void assignIdentity(RequirementReviewRecordId assigned) {
        if (id != null) {
            throw new DomainException("需求评审记录已有 ID，不能重复分配");
        }
        this.id = requireNotNull(assigned, "需求评审记录 ID");
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        String text = value.trim();
        if (text.length() > max) {
            throw new DomainException(field + "不能超过 " + max + " 个字符");
        }
        return text;
    }

    private static String optionalText(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        if (text.length() > max) {
            throw new DomainException(field + "不能超过 " + max + " 个字符");
        }
        return text;
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) throw new DomainException(field + "不能为空");
        return value;
    }

    public RequirementReviewRecordId getId() {
        return id;
    }

    public ProductVersionId getVersionId() {
        return versionId;
    }

    public int getReviewNo() {
        return reviewNo;
    }

    public LocalDate getReviewDate() {
        return reviewDate;
    }

    public String getParticipants() {
        return participants;
    }

    public String getConclusion() {
        return conclusion;
    }

    public String getMinutes() {
        return minutes;
    }

    public RequirementReviewStatus getStatus() {
        return status;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public List<RequirementReviewCandidate> getRequirements() {
        return requirements;
    }

    public UserId getSubmittedBy() {
        return submittedBy;
    }

    public UserId getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
