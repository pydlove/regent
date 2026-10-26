package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.model.RunEvent;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.application.query.ExecutionDiagnostic;

import java.util.List;

/** 执行记录详情：列表行的全部字段 + 末条消息原文 + 事件流。 */
public record TaskRunDetailResponse(
        TaskRunSummaryResponse run,
        ExecutionDiagnosticResponse diagnostic,
        String rawResult,
        List<RunEventResponse> events) {

    public static TaskRunDetailResponse of(TaskRun run,
                                           ExecutionDiagnostic diagnostic,
                                           List<RunEvent> events) {
        return new TaskRunDetailResponse(
                TaskRunSummaryResponse.from(run),
                ExecutionDiagnosticResponse.from(diagnostic),
                run.getRawResult(),
                events.stream().map(RunEventResponse::from).toList());
    }
}
