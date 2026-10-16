package com.pydlove.regent.governance.infrastructure.persistence;

import com.pydlove.regent.governance.domain.model.ApprovalToken;
import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.governance.domain.repository.ApprovalTokenRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/** 审批令牌仓储的 MyBatis 实现。 */
@Repository
public class MyBatisApprovalTokenRepository implements ApprovalTokenRepository {

    private final ApprovalTokenMapper approvalTokenMapper;

    public MyBatisApprovalTokenRepository(ApprovalTokenMapper approvalTokenMapper) {
        this.approvalTokenMapper = approvalTokenMapper;
    }

    @Override
    public Optional<ApprovalToken> findByHash(String tokenHash) {
        return Optional.ofNullable(approvalTokenMapper.selectByHash(tokenHash))
                .map(row -> new ApprovalToken(
                        row.getId(),
                        DecisionId.of(row.getDecisionId()),
                        row.getTokenHash(),
                        row.getStatus(),
                        fromDb(row.getExpiresAt())));
    }

    @Override
    public void consume(long tokenId, String usedIp, Instant usedAt) {
        int affected = approvalTokenMapper.updateConsumed(
                tokenId, usedIp, LocalDateTime.ofInstant(usedAt, ZoneOffset.UTC));
        if (affected == 0) {
            // 并发下第二个请求会走到这里：条件里的 status = 'ACTIVE' 已经不成立。
            // 抛异常让整个事务回滚，而不是让同一枚令牌批两次。
            throw new DomainException("审批令牌已被使用，请重新发起审批");
        }
    }

    private static Instant fromDb(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
