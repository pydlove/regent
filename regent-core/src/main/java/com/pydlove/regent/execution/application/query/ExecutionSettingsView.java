package com.pydlove.regent.execution.application.query;

/**
 * 执行设置的界面读模型。
 *
 * @param maxConcurrentTasks 平台允许同时运行的任务数
 * @param runningTaskCount   当前已占用执行许可的任务数
 */
public record ExecutionSettingsView(int maxConcurrentTasks, int runningTaskCount) {
}
