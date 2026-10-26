package com.pydlove.regent.delivery.interfaces.dto;

import com.pydlove.regent.delivery.application.query.ReleaseView;
import com.pydlove.regent.delivery.domain.model.Release;

import java.time.Instant;
import java.util.List;

public record ReleaseResponse(
        long id,
        long projectId,
        String projectName,
        String version,
        int attemptNo,
        String stage,
        String status,
        String releaseNotes,
        String rollbackPlan,
        Instant createdAt,
        Instant updatedAt,
        BuildArtifactResponse artifact,
        List<ReleaseDeliveryResponse> deliveries,
        List<DeliveryGateResponse> gates) {

    public static ReleaseResponse from(ReleaseView view) {
        Release release = view.release();
        return new ReleaseResponse(
                release.getId().value(),
                release.getProjectId().value(),
                view.projectName(),
                release.getVersion(),
                release.getAttemptNo(),
                release.getStage().name(),
                release.getStatus().name(),
                release.getReleaseNotes(),
                release.getRollbackPlan(),
                release.getCreatedAt(),
                release.getUpdatedAt(),
                BuildArtifactResponse.from(view.artifact()),
                view.deliveries().stream().map(ReleaseDeliveryResponse::from).toList(),
                view.gates().stream().map(DeliveryGateResponse::from).toList());
    }
}
