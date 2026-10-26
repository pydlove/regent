package com.pydlove.regent.review.interfaces.dto;

/** 单个 diff 的通过、要求修改或驳回决策。 */
public record ReviewUnitDecisionRequest(String decision, String comment) {
}
