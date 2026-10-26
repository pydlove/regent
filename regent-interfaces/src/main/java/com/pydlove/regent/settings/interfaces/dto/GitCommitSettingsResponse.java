package com.pydlove.regent.settings.interfaces.dto;

import com.pydlove.regent.settings.domain.model.GitCommitSettings;

/** 企业 Git 提交规范设置。 */
public record GitCommitSettingsResponse(
        boolean enabled,
        String defaultType,
        String defaultScope,
        String subjectTemplate,
        String bodyTemplate,
        String validationPattern,
        String validationHint) {

    public static GitCommitSettingsResponse from(GitCommitSettings settings) {
        return new GitCommitSettingsResponse(
                settings.enabled(),
                settings.defaultType(),
                settings.defaultScope(),
                settings.subjectTemplate(),
                settings.bodyTemplate(),
                settings.validationPattern(),
                settings.validationHint());
    }
}
