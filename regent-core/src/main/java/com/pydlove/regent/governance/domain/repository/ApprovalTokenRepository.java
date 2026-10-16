package com.pydlove.regent.governance.domain.repository;

import com.pydlove.regent.governance.domain.model.ApprovalToken;

import java.time.Instant;
import java.util.Optional;

/**
 * 审批令牌仓储。
 *
 * <p>按<b>哈希</b>查而不是按明文查：明文只在请求里出现一次，从进来到比对完就不再流通，
 * 连日志里都不会有。查得到之后是否可用（过期、已核销）由 {@link ApprovalToken#isUsable} 判断 ——
 * 把过期判断留在领域对象里，边界条件（恰好到点、没有过期时间）才有单测可写，
 * 而不是埋进一句 SQL 的 {@code expires_at > NOW()} 里。
 */
public interface ApprovalTokenRepository {

    Optional<ApprovalToken> findByHash(String tokenHash);

    /** 核销：置为 USED 并记下使用时间与来源 IP。 */
    void consume(long tokenId, String usedIp, Instant usedAt);
}
