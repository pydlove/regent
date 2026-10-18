package com.pydlove.regent.testing.domain.port;

import com.pydlove.regent.execution.domain.model.TaskId;

/**
 * 任务完成后的测试调度口。
 *
 * <p>execution 上下文只认识这个接口，不需要知道测试如何排队、进程怎么起。
 */
public interface TestCaseExecutionScheduler {

    void scheduleAfterTaskCompleted(TaskId taskId);
}
