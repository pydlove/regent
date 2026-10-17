package com.pydlove.regent.plan.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.plan.domain.model.PlanId;
import com.pydlove.regent.plan.domain.model.PlanRun;
import com.pydlove.regent.plan.domain.model.PlanRunId;
import com.pydlove.regent.plan.domain.model.PlanRunItemSnapshot;
import com.pydlove.regent.plan.domain.model.PlanRunSnapshot;
import com.pydlove.regent.plan.domain.model.PlanRunStatus;
import com.pydlove.regent.plan.domain.model.PlanTaskStatus;
import com.pydlove.regent.plan.domain.model.PlanTrigger;

import java.util.List;

/** 计划执行领域对象与数据对象之间的转换。 */
public final class PlanRunDataMapper {

    private PlanRunDataMapper() {
    }

    public static PlanRunData toData(PlanRun run) {
        PlanRunData data = new PlanRunData();
        data.setId(run.getId() == null ? null : run.getId().value());
        data.setPlanId(run.getPlanId().value());
        data.setPlanName(run.getPlanName());
        data.setTriggerType(run.getTrigger().name());
        data.setStatus(run.getStatus().name());
        data.setTaskCount(run.getItems().size());
        data.setProgressCount(run.progressCount());
        data.setSucceededCount(run.count(PlanTaskStatus.SUCCEEDED));
        data.setFailedCount(run.count(PlanTaskStatus.FAILED));
        data.setCancelledCount(run.count(PlanTaskStatus.CANCELLED));
        data.setSkippedCount(run.count(PlanTaskStatus.SKIPPED));
        data.setRunningCount(run.count(PlanTaskStatus.RUNNING) + run.count(PlanTaskStatus.PENDING));
        data.setBlockedCount(run.count(PlanTaskStatus.BLOCKED));
        data.setSummary(run.getSummary());
        data.setStartedAt(PlanDataMapper.toDb(run.getStartedAt()));
        data.setFinishedAt(PlanDataMapper.toDb(run.getFinishedAt()));
        data.setCreatedAt(PlanDataMapper.toDb(run.getCreatedAt()));
        return data;
    }

    public static PlanRunItemData toItemData(PlanRunId runId, PlanRunItemSnapshot item) {
        PlanRunItemData data = new PlanRunItemData();
        data.setPlanRunId(runId.value());
        data.setTaskId(item.taskId().value());
        data.setTaskTitle(item.title());
        data.setStatus(item.status().name());
        data.setDetail(item.detail());
        data.setUpdatedAt(PlanDataMapper.toDb(item.updatedAt()));
        return data;
    }

    public static PlanRun toDomain(PlanRunData data, List<PlanRunItemData> itemData) {
        List<PlanRunItemSnapshot> items = itemData.stream()
                .map(item -> new PlanRunItemSnapshot(
                        TaskId.of(item.getTaskId()),
                        item.getTaskTitle(),
                        PlanTaskStatus.valueOf(item.getStatus()),
                        item.getDetail(),
                        PlanDataMapper.fromDb(item.getUpdatedAt())))
                .toList();
        return PlanRun.restore(new PlanRunSnapshot(
                PlanRunId.of(data.getId()),
                PlanId.of(data.getPlanId()),
                data.getPlanName(),
                PlanTrigger.valueOf(data.getTriggerType()),
                PlanRunStatus.valueOf(data.getStatus()),
                items,
                data.getSummary(),
                PlanDataMapper.fromDb(data.getStartedAt()),
                PlanDataMapper.fromDb(data.getFinishedAt()),
                PlanDataMapper.fromDb(data.getCreatedAt())));
    }
}
