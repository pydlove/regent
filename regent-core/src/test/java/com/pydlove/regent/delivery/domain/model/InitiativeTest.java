package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InitiativeTest {

    @Test
    @DisplayName("立项必须提交后才能批准")
    void 立项必须提交后才能批准() {
        Initiative initiative = Initiative.create(
                ProjectId.of(1), UserId.of(9), "研发流程", "缩短交付周期",
                "标准流程", "不做通用 BPMN", "转测周期缩短");

        assertThrows(DomainException.class, initiative::approve);

        initiative.submit();
        initiative.approve();

        assertEquals(InitiativeStatus.APPROVED, initiative.getStatus());
    }

    @Test
    @DisplayName("已批准立项不能再修改")
    void 已批准立项不能再修改() {
        Initiative initiative = Initiative.create(
                ProjectId.of(1), UserId.of(9), "研发流程", "缩短交付周期",
                null, null, null);
        initiative.submit();
        initiative.approve();

        assertThrows(DomainException.class,
                () -> initiative.update("新标题", "新目标", null, null, null));
    }

    @Test
    @DisplayName("会议纪要在提交前确定，审批不会覆盖")
    void 审批不覆盖已提交的会议纪要() {
        Initiative initiative = Initiative.create(
                ProjectId.of(1), UserId.of(9), "研发流程", "缩短交付周期",
                null, null, null);
        initiative.updateMeetingMinutes("会议结论：同意立项，按当前范围推进。");
        initiative.submit();

        initiative.approve();

        assertEquals(InitiativeStatus.APPROVED, initiative.getStatus());
        assertEquals("会议结论：同意立项，按当前范围推进。", initiative.getMeetingMinutes());
        assertThrows(DomainException.class, initiative::requireEvidenceEditable);
    }

    @Test
    @DisplayName("驳回不会覆盖已提交的会议纪要")
    void 驳回不覆盖已提交的会议纪要() {
        Initiative initiative = Initiative.create(
                ProjectId.of(1), UserId.of(9), "研发流程", "缩短交付周期",
                null, null, null);
        initiative.updateMeetingMinutes("会议结论：范围过大，补充目标后重新提交。");
        initiative.submit();

        initiative.reject();

        assertEquals(InitiativeStatus.REJECTED, initiative.getStatus());
        assertEquals("会议结论：范围过大，补充目标后重新提交。", initiative.getMeetingMinutes());
    }
}
