package com.pydlove.regent.review.interfaces.dto;

/** 完整 diff 按需读取，避免任务首屏携带大文本。 */
public record ReviewDiffResponse(String content, boolean truncated) {
}
