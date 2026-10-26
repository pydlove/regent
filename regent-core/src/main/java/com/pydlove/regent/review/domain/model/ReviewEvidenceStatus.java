package com.pydlove.regent.review.domain.model;

/** 证据状态必须明确，不能把未执行解释成通过。 */
public enum ReviewEvidenceStatus {
    PASSED,
    FAILED,
    NOT_EXECUTED,
    UNAVAILABLE
}
