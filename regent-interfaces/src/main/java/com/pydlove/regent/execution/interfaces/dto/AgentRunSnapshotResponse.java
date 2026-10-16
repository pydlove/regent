package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.application.command.AgentRunSnapshot;

import java.time.Instant;

/** 心跳响应：状态快照 + 一句「现在该干什么」。 */
public record AgentRunSnapshotResponse(long runId,
                                       long taskId,
                                       String runStatus,
                                       String taskStatus,
                                       String nextAction,
                                       String note,
                                       Long decisionId,
                                       String decisionStatus,
                                       int leaseSeconds,
                                       Instant leaseExpiresAt) {

    public static AgentRunSnapshotResponse from(AgentRunSnapshot snapshot) {
        return new AgentRunSnapshotResponse(snapshot.runId(), snapshot.taskId(), snapshot.runStatus(),
                snapshot.taskStatus(), snapshot.nextAction().name(), snapshot.note(), snapshot.decisionId(),
                snapshot.decisionStatus(), snapshot.leaseSeconds(), snapshot.leaseExpiresAt());
    }
}
