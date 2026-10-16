package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.command.ExecutionSettingsService;
import com.pydlove.regent.execution.interfaces.dto.ExecutionSettingsResponse;
import com.pydlove.regent.execution.interfaces.dto.UpdateExecutionSettingsRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台级执行设置接口。
 *
 * <p>读接口同时返回当前活跃任务数，界面需要回答「现在跑了几条、上限是多少」，
 * 而不是只展示一个脱离运行状态的数字。
 */
@RestController
@RequestMapping("/api/execution/settings")
public class ExecutionSettingsController {

    private final ExecutionSettingsService executionSettingsService;

    public ExecutionSettingsController(ExecutionSettingsService executionSettingsService) {
        this.executionSettingsService = executionSettingsService;
    }

    @GetMapping
    public ExecutionSettingsResponse current() {
        return ExecutionSettingsResponse.from(executionSettingsService.current());
    }

    @PutMapping
    public ExecutionSettingsResponse update(@RequestBody UpdateExecutionSettingsRequest request) {
        return ExecutionSettingsResponse.from(
                executionSettingsService.update(request.maxConcurrentTasks()));
    }
}
