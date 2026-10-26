package com.pydlove.regent.delivery.interfaces.dto;

public record DesignModuleRequest(
        String name,
        String description,
        Long assigneeId) {
}
