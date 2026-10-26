package com.pydlove.regent.execution.application.query;

import java.util.List;

/** 控制塔首屏快照。统计与列表来自同一批执行记录，避免头部数字与列表分叉。 */
public record ExecutionControlTowerSnapshot(
        int activeCount,
        int queuedCount,
        int blockedCount,
        int abnormalCount,
        int totalCount,
        List<ExecutionControlTowerEntry> entries) {

    public ExecutionControlTowerSnapshot {
        entries = List.copyOf(entries);
    }
}
