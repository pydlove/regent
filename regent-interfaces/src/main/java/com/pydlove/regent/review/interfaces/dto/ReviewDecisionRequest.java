package com.pydlove.regent.review.interfaces.dto;

/** 人工验收请求；decision 取值 APPROVED / CHANGES_REQUESTED / REJECTED。 */
public record ReviewDecisionRequest(String decision, String comment) {
}
