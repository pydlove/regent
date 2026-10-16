package com.pydlove.regent.governance.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批令牌行。
 *
 * <p>刻意不含 {@code used_ip} / {@code used_at} / {@code created_at}：核销时的写入由一条
 * UPDATE 完成，读出来的东西只用于「能不能用」这一个判断 —— 多带字段只会让人以为
 * 令牌是个需要到处传的对象。
 */
@Data
public class ApprovalTokenRow {

    private Long id;
    private Long decisionId;
    private String tokenHash;
    private String status;
    private LocalDateTime expiresAt;
}
