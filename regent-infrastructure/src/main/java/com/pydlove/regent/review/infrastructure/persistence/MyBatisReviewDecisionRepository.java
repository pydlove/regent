package com.pydlove.regent.review.infrastructure.persistence;

import com.pydlove.regent.review.domain.model.ReviewDecision;
import com.pydlove.regent.review.domain.model.ReviewDecisionId;
import com.pydlove.regent.review.domain.model.ReviewSnapshotId;
import com.pydlove.regent.review.domain.repository.ReviewDecisionRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 审阅结论仓储实现。 */
@Repository
public class MyBatisReviewDecisionRepository implements ReviewDecisionRepository {

    private final ReviewDecisionMapper mapper;

    public MyBatisReviewDecisionRepository(ReviewDecisionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ReviewDecision save(ReviewDecision decision) {
        if (decision.getId() != null) {
            throw new DomainException("审阅结论只追加，不能重复保存：" + decision.getId());
        }
        ReviewDecisionData data = ReviewDecisionDataMapper.toData(decision);
        if (mapper.insert(data) != 1) {
            throw new DomainException("审阅结论新增失败");
        }
        decision.assignIdentity(ReviewDecisionId.of(data.getId()));
        return decision;
    }

    @Override
    public List<ReviewDecision> findBySnapshot(ReviewSnapshotId snapshotId) {
        return mapper.selectBySnapshot(snapshotId.value()).stream()
                .map(ReviewDecisionDataMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<ReviewDecision> findLatestBySnapshot(ReviewSnapshotId snapshotId) {
        return Optional.ofNullable(mapper.selectLatestBySnapshot(snapshotId.value()))
                .map(ReviewDecisionDataMapper::toDomain);
    }
}
