package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequirementDesignModuleTest {

    @Test
    @DisplayName("研发保存并提交自己模块的详细设计")
    void savesAndSubmitsModuleDesign() {
        RequirementDesignModule module = RequirementDesignModule.create(
                RequirementDeliveryId.of(3L),
                "权限模块",
                "负责角色校验和审计",
                UserId.of(8L),
                UserId.of(9L));

        module.saveDesign("权限模型", "新增角色校验和审计日志");
        module.submit();

        assertEquals(DesignModuleStatus.SUBMITTED, module.getStatus());
        assertEquals("权限模型", module.getDesignSummary());
    }

    @Test
    @DisplayName("已提交模块不能直接修改，需先退回")
    void submittedModuleMustBeReopenedBeforeEditing() {
        RequirementDesignModule module = RequirementDesignModule.create(
                RequirementDeliveryId.of(3L),
                "接口模块",
                null,
                UserId.of(8L),
                UserId.of(9L));
        module.saveDesign("接口", "接口设计");
        module.submit();

        assertThrows(DomainException.class, () -> module.saveDesign("修改", "修改"));
        module.reopen();
        module.saveDesign("修改", "修改后的设计");

        assertEquals(DesignModuleStatus.REVISION_REQUIRED, module.getStatus());
    }
}
