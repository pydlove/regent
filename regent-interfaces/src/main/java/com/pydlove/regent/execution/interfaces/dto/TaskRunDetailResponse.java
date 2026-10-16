package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.model.RunEvent;
import com.pydlove.regent.execution.domain.model.TaskRun;

import java.util.List;

/** 执行记录详情：列表行的全部字段 + 末条消息原文 + 事件流。 */
public record TaskRunDetailResponse(
        TaskRunSummaryResponse run,
        String rawResult,
        List<RunEventResponse> events) {

    public static TaskRunDetailResponse of(TaskRun run, List<RunEvent> events) {
        return new TaskRunDetailResponse(
                TaskRunSummaryResponse.from(run),
                run.getRawResult(),
                events.stream().map(RunEventResponse::from).toList());
    }
}
