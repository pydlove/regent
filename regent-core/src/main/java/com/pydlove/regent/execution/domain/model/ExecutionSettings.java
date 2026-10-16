package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/**
 * 执行链路的平台级设置。
 *
 * <p>当前只包含同时运行的任务数上限。这个值会直接决定工作区并发压力，因此校验放在领域对象里，
 * 不依赖配置文件、数据库约束或界面输入框。
 */
public record ExecutionSettings(int maxConcurrentTasks) {

    public static final int MIN_CONCURRENT_TASKS = 1;
    public static final int MAX_CONCURRENT_TASKS = 64;

    public ExecutionSettings {
        if (maxConcurrentTasks < MIN_CONCURRENT_TASKS || maxConcurrentTasks > MAX_CONCURRENT_TASKS) {
            throw new DomainException("同时运行任务数必须在 " + MIN_CONCURRENT_TASKS + " 到 "
                    + MAX_CONCURRENT_TASKS + " 之间：" + maxConcurrentTasks);
        }
    }
}
