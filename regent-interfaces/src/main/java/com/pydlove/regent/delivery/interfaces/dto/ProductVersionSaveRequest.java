package com.pydlove.regent.delivery.interfaces.dto;

public record ProductVersionSaveRequest(
        Long projectId,
        String versionNo,
        String name,
        String description) {
}
