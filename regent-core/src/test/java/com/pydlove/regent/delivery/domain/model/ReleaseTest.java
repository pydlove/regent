package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReleaseTest {

    @Test
    @DisplayName("发布必须逐阶段推进，不能跳过转测")
    void 发布必须逐阶段推进() {
        Release release = Release.create(
                ProjectId.of(1), "v1.0.0", 1, BuildArtifactId.of(2),
                List.of(RequirementDeliveryId.of(3)), "发布说明", "回滚到上一版本");

        assertThrows(RuntimeException.class, release::passIntegration);

        release.approveTransfer();
        release.passIntegration();
        release.passRegression();
        release.approveRelease();
        release.markDeployed();
        release.close();

        assertEquals(ReleaseStage.CLOSED, release.getStage());
        assertEquals(ReleaseStatus.RELEASED, release.getStatus());
    }

    @Test
    @DisplayName("发布必须填写回滚方案")
    void 发布必须填写回滚方案() {
        assertThrows(RuntimeException.class, () -> Release.create(
                ProjectId.of(1), "v1.0.0", 1, BuildArtifactId.of(2),
                List.of(RequirementDeliveryId.of(3)), null, ""));
    }
}
