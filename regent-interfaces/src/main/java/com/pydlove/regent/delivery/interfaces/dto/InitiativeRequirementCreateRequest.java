package com.pydlove.regent.delivery.interfaces.dto;

/** 从已批准立项直接录入需求，并自动建立需求评审交付。 */
public record InitiativeRequirementCreateRequest(
        String title,
        String rawText,
        String acceptanceText,
        String clarificationText) {
}
