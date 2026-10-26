package com.pydlove.regent.delivery.interfaces.dto;

import java.util.List;

public record ReleaseCreateRequest(
        Long projectId,
        String version,
        String gitCommit,
        String digest,
        Long sizeBytes,
        String storageRef,
        String releaseNotes,
        String rollbackPlan,
        List<Long> deliveryIds) {
}
