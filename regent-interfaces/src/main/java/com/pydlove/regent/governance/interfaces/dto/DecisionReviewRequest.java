package com.pydlove.regent.governance.interfaces.dto;

/**
 * 审批请求体。
 *
 * <p>三个字段都可空，因为它们各有各的可空含义：{@code choice} 为空表示采纳 AI 的建议
 * （邮件里点一下就该能办成事），{@code note} 为空表示没留额外说明，
 * {@code token} 为空表示这是控制台路径而不是邮件链接。
 */
public record DecisionReviewRequest(String choice, String note, String token) {
}
