package com.pydlove.regent.review.infrastructure.persistence;

import com.pydlove.regent.review.domain.model.ReviewChangeUnit;
import com.pydlove.regent.review.domain.model.ReviewChangeUnitId;
import com.pydlove.regent.review.domain.model.ReviewEvidence;
import com.pydlove.regent.review.domain.model.ReviewEvidenceId;
import com.pydlove.regent.review.domain.model.ReviewFinding;
import com.pydlove.regent.review.domain.model.ReviewFindingId;
import com.pydlove.regent.review.domain.model.ReviewSnapshot;
import com.pydlove.regent.review.domain.model.ReviewSnapshotId;
import com.pydlove.regent.review.domain.model.ReviewTaskId;
import com.pydlove.regent.review.domain.model.ReviewTaskRunId;
import com.pydlove.regent.review.domain.repository.ReviewSnapshotRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 审阅快照聚合的 MyBatis 实现。 */
@Repository
public class MyBatisReviewSnapshotRepository implements ReviewSnapshotRepository {

    private final ReviewSnapshotMapper mapper;

    public MyBatisReviewSnapshotRepository(ReviewSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ReviewSnapshot> findById(ReviewSnapshotId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(this::load);
    }

    @Override
    public Optional<ReviewSnapshot> findByTask(ReviewTaskId taskId) {
        return findCurrentByTask(taskId);
    }

    @Override
    public Optional<ReviewSnapshot> findCurrentByTask(ReviewTaskId taskId) {
        return Optional.ofNullable(mapper.selectLatestByTask(taskId.value())).map(this::load);
    }

    @Override
    public Optional<ReviewSnapshot> findByTaskRun(ReviewTaskRunId taskRunId) {
        return Optional.ofNullable(mapper.selectByTaskRun(taskRunId.value())).map(this::load);
    }

    @Override
    public ReviewSnapshot save(ReviewSnapshot snapshot) {
        ReviewSnapshotData data = ReviewSnapshotDataMapper.toData(snapshot);
        if (snapshot.getId() == null) {
            if (mapper.insert(data) != 1) {
                throw new DomainException("审阅快照新增失败");
            }
            snapshot.assignIdentity(ReviewSnapshotId.of(data.getId()));
        } else if (mapper.update(data) != 1) {
            throw new DomainException("审阅快照更新失败：" + snapshot.getId());
        }

        long snapshotId = snapshot.getId().value();
        for (ReviewChangeUnit unit : snapshot.getChangeUnits()) {
            ReviewChangeUnitData unitData = ReviewSnapshotDataMapper.toData(
                    unit, snapshotId, snapshot.getCreatedAt());
            if (unit.getId() == null) {
                if (mapper.insertUnit(unitData) != 1) {
                    throw new DomainException("变更单元新增失败：" + unit.getTitle());
                }
                unit.assignIdentity(ReviewChangeUnitId.of(unitData.getId()));
            } else if (mapper.updateUnit(unitData) != 1) {
                throw new DomainException("变更单元更新失败：" + unit.getId());
            }
        }
        for (ReviewEvidence evidence : snapshot.getEvidence()) {
            ReviewEvidenceData evidenceData = ReviewSnapshotDataMapper.toData(
                    evidence, snapshotId, snapshot.getCreatedAt());
            if (evidence.getId() == null) {
                if (mapper.insertEvidence(evidenceData) != 1) {
                    throw new DomainException("审阅证据新增失败：" + evidence.getTitle());
                }
                evidence.assignIdentity(ReviewEvidenceId.of(evidenceData.getId()));
            } else if (mapper.updateEvidence(evidenceData) != 1) {
                throw new DomainException("审阅证据更新失败：" + evidence.getId());
            }
        }
        for (ReviewFinding finding : snapshot.getFindings()) {
            ReviewFindingData findingData = ReviewSnapshotDataMapper.toData(
                    finding, snapshotId, snapshot.getCreatedAt());
            if (finding.getId() == null) {
                if (mapper.insertFinding(findingData) != 1) {
                    throw new DomainException("审阅发现新增失败：" + finding.getTitle());
                }
                finding.assignIdentity(ReviewFindingId.of(findingData.getId()));
            } else if (mapper.updateFinding(findingData) != 1) {
                throw new DomainException("审阅发现更新失败：" + finding.getId());
            }
        }
        return snapshot;
    }

    @Override
    public List<ReviewSnapshot> findUnexpiredByTask(ReviewTaskId taskId) {
        return mapper.selectUnexpiredByTask(taskId.value()).stream()
                .map(this::load)
                .toList();
    }

    private ReviewSnapshot load(ReviewSnapshotData data) {
        return ReviewSnapshotDataMapper.toDomain(
                data,
                mapper.selectUnits(data.getId()),
                mapper.selectFindings(data.getId()),
                mapper.selectEvidence(data.getId()));
    }
}
