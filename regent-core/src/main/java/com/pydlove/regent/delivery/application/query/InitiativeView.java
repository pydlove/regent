package com.pydlove.regent.delivery.application.query;

import com.pydlove.regent.delivery.domain.model.Initiative;

public record InitiativeView(
        Initiative initiative,
        String projectName,
        InitiativePlanView plan) {

    public InitiativeView(Initiative initiative, String projectName) {
        this(initiative, projectName, null);
    }
}
