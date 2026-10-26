package com.pydlove.regent.execution.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.execution.domain.model.RunEvent;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.repository.RunEventRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 执行事件流仓储的 MyBatis 实现。
 *
 * <p>{@code payload} 列是 JSON 类型：MySQL 会拒绝任何不是合法 JSON 的文本。上游给的原文
 * 理论上都是 JSON（codex 每行一个对象，平台事件自己拼），但「理论上」不足以让这里
 * 直接信任它 —— 一条解析不了的行不该让整次执行的事件写入全部失败，所以这里兜一层：
 * 解析不了就把它包成一个 JSON 字符串，宁可难看也不能丢。
 */
@Repository
public class MyBatisRunEventRepository implements RunEventRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RunEventMapper runEventMapper;
    private final TaskRunMapper taskRunMapper;

    public MyBatisRunEventRepository(RunEventMapper runEventMapper, TaskRunMapper taskRunMapper) {
        this.runEventMapper = runEventMapper;
        this.taskRunMapper = taskRunMapper;
    }

    @Override
    public void append(TaskRunId runId, RunEvent event) {
        RunEventData data = new RunEventData();
        data.setTaskRunId(runId.value());
        data.setSeq(event.seq());
        data.setEventType(event.type());
        data.setPayload(asJson(event.payloadJson()));
        data.setOccurredAt(LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC));
        data.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        runEventMapper.insert(data);
        if (event.occurredAt() != null) {
            taskRunMapper.updateLastEvent(
                    runId.value(),
                    LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC));
        }
    }

    @Override
    public List<RunEvent> findByRun(TaskRunId runId) {
        return runEventMapper.selectByRun(runId.value()).stream()
                .map(data -> new RunEvent(data.getSeq(), data.getEventType(), data.getPayload(),
                        data.getOccurredAt().toInstant(ZoneOffset.UTC)))
                .toList();
    }

    private String asJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            JSON.readTree(payload);
            return payload;
        } catch (JsonProcessingException e) {
            try {
                return JSON.writeValueAsString(payload);
            } catch (JsonProcessingException impossible) {
                return null;
            }
        }
    }
}
