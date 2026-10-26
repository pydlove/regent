package com.pydlove.regent.settings.interfaces.dto;

/** 更新企业 Git 提交规范；所有正则与长度都在领域层重新校验。 */
public record UpdateGitCommitSettingsRequest(
        boolean enabled,
        String defaultType,
        String defaultScope,
        String subjectTemplate,
        String bodyTemplate,
        String validationPattern,
        String validationHint) {
}
