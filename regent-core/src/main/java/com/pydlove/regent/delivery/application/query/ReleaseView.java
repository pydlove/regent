package com.pydlove.regent.delivery.application.query;

import com.pydlove.regent.delivery.domain.model.BuildArtifact;
import com.pydlove.regent.delivery.domain.model.DeliveryGate;
import com.pydlove.regent.delivery.domain.model.Release;

import java.util.List;

public record ReleaseView(
        Release release,
        BuildArtifact artifact,
        String projectName,
        List<RequirementDeliveryView> deliveries,
        List<DeliveryGate> gates) {
}
