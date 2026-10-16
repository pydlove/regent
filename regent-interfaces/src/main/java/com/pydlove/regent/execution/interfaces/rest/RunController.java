package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.query.TaskRunQueryService;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.interfaces.dto.TaskRunDetailResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 执行记录接口。
 *
 * <p>单独一个控制器而不是塞进 {@code TaskController}：执行记录是「任务的第 N 次尝试」，
 * 有自己的 ID 空间（前端点开某一次运行拿的就是 runId），挂在任务下面会让 URL 变成
 * {@code /api/tasks/{taskId}/runs/{runId}} —— 那个 taskId 在查询里根本用不上。
 */
@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final TaskRunQueryService taskRunQueryService;

    public RunController(TaskRunQueryService taskRunQueryService) {
        this.taskRunQueryService = taskRunQueryService;
    }

    @GetMapping("/{id}")
    public TaskRunDetailResponse detail(@PathVariable long id) {
        TaskRunId runId = TaskRunId.of(id);
        return TaskRunDetailResponse.of(taskRunQueryService.get(runId), taskRunQueryService.events(runId));
    }
}
