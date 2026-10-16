package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.application.query.ExecutionSettingsView;

/** 平台执行设置的前端契约。 */
public record ExecutionSettingsResponse(int maxConcurrentTasks, int runningTaskCount) {

    public static ExecutionSettingsResponse from(ExecutionSettingsView view) {
        return new ExecutionSettingsResponse(view.maxConcurrentTasks(), view.runningTaskCount());
    }
}
