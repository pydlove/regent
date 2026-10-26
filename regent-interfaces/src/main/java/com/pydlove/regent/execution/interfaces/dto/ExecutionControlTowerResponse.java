package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.application.query.ExecutionControlTowerSnapshot;

import java.util.List;

/** 执行控制塔首屏快照。 */
public record ExecutionControlTowerResponse(
        int activeCount,
        int queuedCount,
        int blockedCount,
        int abnormalCount,
        int totalCount,
        List<ExecutionControlTowerEntryResponse> entries) {

    public static ExecutionControlTowerResponse from(ExecutionControlTowerSnapshot snapshot) {
        return new ExecutionControlTowerResponse(
                snapshot.activeCount(),
                snapshot.queuedCount(),
                snapshot.blockedCount(),
                snapshot.abnormalCount(),
                snapshot.totalCount(),
                snapshot.entries().stream()
                        .map(ExecutionControlTowerEntryResponse::from)
                        .toList());
    }
}
