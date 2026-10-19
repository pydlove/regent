package com.pydlove.regent.bug.infrastructure.persistence;

import com.pydlove.regent.bug.domain.model.BugSeverity;
import com.pydlove.regent.bug.domain.model.BugSource;
import com.pydlove.regent.bug.domain.model.BugStatus;
import com.pydlove.regent.bug.domain.repository.BugPage;
import com.pydlove.regent.bug.domain.repository.BugQuery;
import com.pydlove.regent.bug.domain.repository.BugQueryRepository;
import com.pydlove.regent.bug.domain.repository.BugView;
import com.pydlove.regent.identity.domain.model.UserId;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 缺陷单读侧仓储实现。 */
@Repository
public class MyBatisBugQueryRepository implements BugQueryRepository {

    private final BugMapper bugMapper;

    public MyBatisBugQueryRepository(BugMapper bugMapper) {
        this.bugMapper = bugMapper;
    }

    @Override
    public BugPage findPage(BugQuery query) {
        String severity = query.severity() == null ? null : query.severity().name();
        Long projectId = query.projectId() == null ? null : query.projectId().value();
        String scope = query.scope().name();
        String keyword = escapeLike(query.keyword());
        Long ownerId = query.ownerId() == null ? null : query.ownerId().value();
        long total = bugMapper.countBy(severity, projectId, scope, keyword, ownerId);
        if (total == 0) {
            return new BugPage(List.of(), 0, query.page(), query.size());
        }
        List<BugView> items = bugMapper.selectPage(
                        severity, projectId, scope, keyword, ownerId, query.size(), query.offset())
                .stream()
                .map(MyBatisBugQueryRepository::toView)
                .toList();
        return new BugPage(items, total, query.page(), query.size());
    }

    @Override
    public Optional<BugView> findById(long id) {
        return Optional.ofNullable(bugMapper.selectRowById(id))
                .map(MyBatisBugQueryRepository::toView);
    }

    @Override
    public Optional<BugView> findByIdOwned(long id, UserId ownerId) {
        return Optional.ofNullable(bugMapper.selectRowByIdOwned(id, ownerId.value()))
                .map(MyBatisBugQueryRepository::toView);
    }

    private static BugView toView(BugRow row) {
        return new BugView(
                row.getId(),
                row.getProjectId(),
                row.getProjectName(),
                row.getRequirementId(),
                row.getSourceTaskId(),
                row.getFixTaskId(),
                BugSource.valueOf(row.getSource()),
                row.getTitle(),
                BugSeverity.valueOf(row.getSeverity()),
                BugStatus.valueOf(row.getStatus()),
                row.getReproduction(),
                row.getFilePath(),
                row.getDetail(),
                fromDb(row.getFoundAt()),
                fromDb(row.getFixedAt()),
                row.getWontFixReason());
    }

    private static Instant fromDb(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static String escapeLike(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
