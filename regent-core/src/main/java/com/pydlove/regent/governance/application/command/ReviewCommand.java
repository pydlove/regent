package com.pydlove.regent.governance.application.command;

import java.util.Optional;

/**
 * 一次人工审批请求。
 *
 * @param choice   选择的方案；为空表示采纳 AI 的建议 —— 邮件里点一下就批准，不该逼人先选一项
 * @param note     补充说明；驳回时必填（由聚合守卫）
 * @param token    一次性审批令牌的明文；控制台路径可以为空
 * @param clientIp 来源 IP，核销令牌时留痕
 */
public record ReviewCommand(String choice, String note, String token, String clientIp) {

    public static ReviewCommand of(String choice, String note) {
        return new ReviewCommand(choice, note, null, null);
    }

    public Optional<String> tokenOrEmpty() {
        return Optional.ofNullable(token);
    }
}
