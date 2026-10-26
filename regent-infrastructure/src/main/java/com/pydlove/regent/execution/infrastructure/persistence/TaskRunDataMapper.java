package com.pydlove.regent.execution.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.model.TaskRunSnapshot;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.TokenUsage;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 执行记录：领域对象 ↔ 数据对象。
 *
 * <p>改动文件清单以 JSON 存一列而不是另开一张关联表：它只在「这次改了什么」这个问题上被整体读写，
 * 从不按单个文件反查。开表就要维护外键、顺序和去重，换来的查询能力一次都用不上。
 */
public final class TaskRunDataMapper {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private TaskRunDataMapper() {
    }

    public static TaskRunData toData(TaskRun run) {
        TaskRunData data = new TaskRunData();
        data.setId(run.getId() == null ? null : run.getId().value());
        data.setTaskId(run.getTaskId().value());
        data.setAttemptNo(run.getAttemptNo());
        data.setBaseRef(run.getBaseRef());
        data.setStatus(run.getStatus().name());
        data.setExitCode(run.getExitCode());
        data.setTokenInput(run.getTokens().input());
        data.setTokenCachedInput(run.getTokens().cachedInput());
        data.setTokenOutput(run.getTokens().output());
        data.setBranchName(run.getBranchName());
        data.setExecutor(run.getExecutor());
        data.setAgentName(run.getAgentName());
        data.setDeviceId(run.getDeviceId());
        data.setLeaseExpiresAt(toDb(run.getLeaseExpiresAt()));
        data.setLastHeartbeatAt(toDb(run.getLastHeartbeatAt()));
        data.setLastEventAt(toDb(run.getLastEventAt()));
        data.setSummary(run.getSummary());
        data.setRawResult(run.getRawResult());
        data.setSchemaValid(run.isSchemaValid());
        data.setChangedFiles(toJson(run.getChangedFiles()));
        data.setStartedAt(toDb(run.getStartedAt()));
        data.setFinishedAt(toDb(run.getFinishedAt()));
        data.setCreatedAt(toDb(run.getCreatedAt()));
        return data;
    }

    public static TaskRun toDomain(TaskRunData data) {
        return TaskRun.restore(new TaskRunSnapshot(
                TaskRunId.of(data.getId()),
                TaskId.of(data.getTaskId()),
                data.getAttemptNo(),
                data.getBranchName(),
                data.getBaseRef(),
                RunStatus.valueOf(data.getStatus()),
                data.getExitCode(),
                TokenUsage.of(nullToZero(data.getTokenInput()),
                        nullToZero(data.getTokenCachedInput()),
                        nullToZero(data.getTokenOutput())),
                data.getSummary(),
                data.getRawResult(),
                Boolean.TRUE.equals(data.getSchemaValid()),
                fromJson(data.getChangedFiles()),
                data.getExecutor(),
                data.getAgentName(),
                data.getDeviceId(),
                fromDb(data.getLeaseExpiresAt()),
                fromDb(data.getLastHeartbeatAt()),
                fromDb(data.getLastEventAt()),
                fromDb(data.getStartedAt()),
                fromDb(data.getFinishedAt()),
                fromDb(data.getCreatedAt())));
    }

    private static String toJson(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(paths);
        } catch (JsonProcessingException e) {
            // 路径来自执行器的规范化输出，序列化失败说明数据本身已经坏了 —— 报错而不是静默丢
            throw new DomainException("改动文件清单无法序列化", e);
        }
    }

    private static List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            throw new DomainException("改动文件清单不是合法的 JSON 数组：" + json, e);
        }
    }

    private static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant fromDb(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
