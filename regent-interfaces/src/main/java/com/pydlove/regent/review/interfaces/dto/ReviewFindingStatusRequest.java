package com.pydlove.regent.review.interfaces.dto;

/** 发现状态请求：PENDING / CONFIRMED / FALSE_POSITIVE / FIXED。 */
public record ReviewFindingStatusRequest(String status) {
}
