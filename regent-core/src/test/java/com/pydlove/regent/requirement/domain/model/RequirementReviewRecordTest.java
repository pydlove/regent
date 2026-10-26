package com.pydlove.regent.requirement.domain.model;

import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequirementReviewRecordTest {

    private static final UserId REVIEWER = UserId.of(1L);

    @Test
    @DisplayName("提交需求评审时保存材料并进入待审核")
    void submitsReviewMaterials() {
        RequirementReviewRecord record = RequirementReviewRecord.submit(
                ProductVersionId.of(7L),
                2,
                LocalDate.of(2026, 10, 26),
                "确认范围和验收标准。",
                List.of(new RequirementReviewCandidate(RequirementId.of(11L), 11, "需求 A")),
                REVIEWER);

        assertEquals(2, record.getReviewNo());
        assertEquals(1, record.getRequirements().size());
        assertEquals("确认范围和验收标准。", record.getMinutes());
        assertEquals(RequirementReviewStatus.SUBMITTED, record.getStatus());
        assertEquals("待审核", record.getConclusion());
    }

    @Test
    @DisplayName("评审日期不能晚于今天，且必须包含需求")
    void validatesDateAndRequirements() {
        assertThrows(DomainException.class, () -> RequirementReviewRecord.submit(
                ProductVersionId.of(7L),
                1,
                LocalDate.now().plusDays(1),
                "纪要",
                List.of(new RequirementReviewCandidate(RequirementId.of(11L), 11, "需求 A")),
                REVIEWER));

        assertThrows(DomainException.class, () -> RequirementReviewRecord.submit(
                ProductVersionId.of(7L),
                1,
                LocalDate.now(),
                "纪要",
                List.of(),
                REVIEWER));
    }

    @Test
    @DisplayName("项目负责人审核通过后记录意见，不能重复审核")
    void approvesSubmittedReview() {
        RequirementReviewRecord record = RequirementReviewRecord.submit(
                ProductVersionId.of(7L),
                1,
                LocalDate.now(),
                "纪要",
                List.of(new RequirementReviewCandidate(RequirementId.of(11L), 11, "需求 A")),
                REVIEWER);

        record.approve(REVIEWER, "材料齐全，同意通过");

        assertEquals(RequirementReviewStatus.APPROVED, record.getStatus());
        assertEquals("评审通过", record.getConclusion());
        assertEquals("材料齐全，同意通过", record.getReviewComment());
        assertEquals(REVIEWER, record.getDecidedBy());
        assertThrows(DomainException.class,
                () -> record.approve(REVIEWER, "再次通过"));
    }

    @Test
    @DisplayName("驳回需求评审必须填写审核意见")
    void rejectsWithRequiredComment() {
        RequirementReviewRecord record = RequirementReviewRecord.submit(
                ProductVersionId.of(7L),
                1,
                LocalDate.now(),
                "纪要",
                List.of(new RequirementReviewCandidate(RequirementId.of(11L), 11, "需求 A")),
                REVIEWER);

        assertThrows(DomainException.class, () -> record.reject(REVIEWER, " "));
        record.reject(REVIEWER, "验收标准不完整");

        assertEquals(RequirementReviewStatus.REJECTED, record.getStatus());
        assertEquals("验收标准不完整", record.getReviewComment());
    }
}
