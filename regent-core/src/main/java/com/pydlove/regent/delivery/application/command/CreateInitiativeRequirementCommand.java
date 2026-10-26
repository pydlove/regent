package com.pydlove.regent.delivery.application.command;

/** 在已批准立项下录入一条需求，并立即建立研发交付。 */
public record CreateInitiativeRequirementCommand(
        String title,
        String rawText,
        String acceptanceText,
        String clarificationText) {
}
