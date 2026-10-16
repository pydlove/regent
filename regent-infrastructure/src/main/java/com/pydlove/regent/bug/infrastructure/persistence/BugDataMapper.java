package com.pydlove.regent.bug.infrastructure.persistence;

import com.pydlove.regent.bug.domain.model.Bug;
import com.pydlove.regent.bug.domain.model.BugId;
import com.pydlove.regent.bug.domain.model.BugSeverity;
import com.pydlove.regent.bug.domain.model.BugSnapshot;
import com.pydlove.regent.bug.domain.model.BugSource;
import com.pydlove.regent.bug.domain.model.BugStatus;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.RequirementId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 缺陷领域对象与数据对象之间的唯一转换点。 */
public final class BugDataMapper {

    private BugDataMapper() {
    }

    public static BugData toData(Bug bug) {
        BugData data = new BugData();
        data.setId(bug.getId() == null ? null : bug.getId().value());
        data.setProjectId(bug.getProjectId().value());
        data.setRequirementId(bug.getRequirementId().map(RequirementId::value).orElse(null));
        data.setSourceTaskId(bug.getSourceTaskId().map(TaskId::value).orElse(null));
        data.setFixTaskId(bug.getFixTaskId().map(TaskId::value).orElse(null));
        data.setSource(bug.getSource().name());
        data.setTitle(bug.getTitle());
        data.setSeverity(bug.getSeverity().name());
        data.setStatus(bug.getStatus().name());
        data.setReproduction(bug.getReproduction().orElse(null));
        data.setFilePath(bug.getFilePath().orElse(null));
        data.setDetail(bug.getDetail().orElse(null));
        data.setWontFixReason(bug.getWontFixReason().orElse(null));
        data.setFoundAt(toDb(bug.getFoundAt()));
        data.setFixedAt(toDb(bug.getFixedAt().orElse(null)));
        data.setCreatedAt(toDb(bug.getCreatedAt()));
        data.setUpdatedAt(toDb(bug.getUpdatedAt()));
        return data;
    }

    public static Bug toDomain(BugData data) {
        return Bug.restore(new BugSnapshot(
                BugId.of(data.getId()),
                ProjectId.of(data.getProjectId()),
                optionalId(data.getRequirementId()).map(RequirementId::of).orElse(null),
                optionalId(data.getSourceTaskId()).map(TaskId::of).orElse(null),
                optionalId(data.getFixTaskId()).map(TaskId::of).orElse(null),
                BugSource.valueOf(data.getSource()),
                data.getTitle(),
                BugSeverity.valueOf(data.getSeverity()),
                BugStatus.valueOf(data.getStatus()),
                data.getReproduction(),
                data.getFilePath(),
                data.getDetail(),
                fromDb(data.getFoundAt()),
                fromDb(data.getFixedAt()),
                data.getWontFixReason(),
                fromDb(data.getCreatedAt()),
                fromDb(data.getUpdatedAt())));
    }

    private static java.util.Optional<Long> optionalId(Long value) {
        return java.util.Optional.ofNullable(value);
    }

    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant fromDb(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
