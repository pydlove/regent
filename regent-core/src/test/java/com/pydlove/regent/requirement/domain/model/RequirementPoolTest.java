package com.pydlove.regent.requirement.domain.model;

import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequirementPoolTest {

    private static final ProjectId PROJECT = ProjectId.of(7L);
    private static final UserId REVIEWER = UserId.of(2L);

    @Test
    @DisplayName("需求先纳入版本，再提交评审并通过")
    void completesPoolReview() {
        Requirement requirement = requirement();

        requirement.assignVersion(ProductVersionId.of(11L));
        requirement.submitPoolReview();
        requirement.approvePoolReview(REVIEWER, "范围和验收标准明确");

        assertEquals(RequirementPoolStatus.FORMAL, requirement.getPoolStatus());
        assertEquals(REVIEWER, requirement.getReviewedBy());
        assertEquals("范围和验收标准明确", requirement.getReviewComment());
    }

    @Test
    @DisplayName("未纳入版本的需求不能提交评审")
    void rejectsReviewWithoutVersion() {
        Requirement requirement = requirement();

        DomainException exception = assertThrows(
                DomainException.class, requirement::submitPoolReview);

        assertEquals("需求加入产品版本后才能提交评审", exception.getMessage());
    }

    @Test
    @DisplayName("驳回必须填写原因，且可以修正后重新提交")
    void rejectsWithReasonAndAllowsResubmit() {
        Requirement requirement = requirement();
        requirement.assignVersion(ProductVersionId.of(11L));
        requirement.submitPoolReview();

        assertThrows(DomainException.class,
                () -> requirement.rejectPoolReview(REVIEWER, " "));

        requirement.rejectPoolReview(REVIEWER, "验收标准不可验证");
        assertEquals(RequirementPoolStatus.REJECTED, requirement.getPoolStatus());

        requirement.submitPoolReview();
        assertEquals(RequirementPoolStatus.IN_REVIEW, requirement.getPoolStatus());
    }

    @Test
    @DisplayName("评审中或正式需求不能再修改原始描述")
    void freezesReviewedRequirement() {
        Requirement requirement = requirement();
        requirement.assignVersion(ProductVersionId.of(11L));
        requirement.submitPoolReview();

        assertThrows(DomainException.class, () -> requirement.updateDraft(
                "新标题", "新描述", null, null));

        requirement.approvePoolReview(REVIEWER, null);
        assertThrows(DomainException.class, requirement::removeFromVersion);
    }

    private static Requirement requirement() {
        return Requirement.create(
                PROJECT,
                null,
                "组织数据隔离",
                "不同组织之间不能看到彼此数据",
                "A 组织成员无法查询 B 组织项目",
                null,
                RequirementSource.MANUAL,
                REVIEWER);
    }
}
