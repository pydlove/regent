package com.pydlove.regent.plan.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.plan.domain.model.Plan;
import com.pydlove.regent.plan.domain.model.PlanId;
import com.pydlove.regent.plan.domain.model.PlanSnapshot;
import com.pydlove.regent.plan.domain.model.PlanStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 计划领域对象与数据对象之间的转换。 */
public final class PlanDataMapper {

    private PlanDataMapper() {
    }

    public static PlanData toData(Plan plan) {
        PlanData data = new PlanData();
        data.setId(plan.getId() == null ? null : plan.getId().value());
        data.setUserId(plan.getOwnerId() == null ? null : plan.getOwnerId().value());
        data.setName(plan.getName());
        data.setStatus(plan.getStatus().name());
        data.setScheduledAt(toDb(plan.getScheduledAt()));
        data.setStartedAt(toDb(plan.getStartedAt()));
        data.setFinishedAt(toDb(plan.getFinishedAt()));
        data.setCreatedAt(toDb(plan.getCreatedAt()));
        data.setUpdatedAt(toDb(plan.getUpdatedAt()));
        return data;
    }

    public static Plan toDomain(PlanData data, List<Long> taskIds) {
        return Plan.restore(new PlanSnapshot(
                PlanId.of(data.getId()),
                data.getUserId() == null ? null : UserId.of(data.getUserId()),
                data.getName(),
                taskIds.stream().map(TaskId::of).toList(),
                PlanStatus.valueOf(data.getStatus()),
                fromDb(data.getScheduledAt()),
                fromDb(data.getStartedAt()),
                fromDb(data.getFinishedAt()),
                fromDb(data.getCreatedAt()),
                fromDb(data.getUpdatedAt())));
    }

    static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    static Instant fromDb(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
