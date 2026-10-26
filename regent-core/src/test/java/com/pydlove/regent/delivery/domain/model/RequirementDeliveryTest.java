package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.RequirementId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementDeliveryTest {

    @Test
    @DisplayName("需求交付按门禁完整推进到待转测")
    void 需求交付按门禁完整推进到待转测() {
        RequirementDelivery delivery = RequirementDelivery.create(
                InitiativeId.of(1), ProjectId.of(2), RequirementId.of(3), UserId.of(9));

        assertThrows(DomainException.class, delivery::completeDevelopment);

        delivery.approveRequirementReview();
        delivery.submitDesign("设计摘要", "详细设计内容");
        delivery.approveDesign();
        delivery.completeDevelopment();
        delivery.passSmoke();
        delivery.passAutomation();

        assertEquals(DeliveryStage.READY_FOR_TRANSFER, delivery.getStage());
        assertTrue(delivery.readyForRelease());
    }

    @Test
    @DisplayName("设计为空不能提交设计评审")
    void 设计为空不能提交设计评审() {
        RequirementDelivery delivery = RequirementDelivery.create(
                InitiativeId.of(1), ProjectId.of(2), RequirementId.of(3), UserId.of(9));
        delivery.approveRequirementReview();

        assertThrows(DomainException.class, () -> delivery.submitDesign("", ""));
        assertEquals(DeliveryStage.DESIGN, delivery.getStage());
    }
}
