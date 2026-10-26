package com.pydlove.regent.review.domain.model;

/** 可关联到审阅快照的验证证据类型。 */
public enum ReviewEvidenceType {
    TEST,
    BUILD,
    LOG,
    SCREENSHOT,
    API,
    DATABASE_MIGRATION,
    DEPLOYMENT_PRECHECK
}
