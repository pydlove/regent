package com.pydlove.regent.execution.application.query;

import com.pydlove.regent.execution.domain.model.ExecutorRuntimeStatus;
import com.pydlove.regent.execution.domain.port.ExecutorRuntimeProbe;

/** 设置页的执行器自检入口。检测本身有副作用（启动一个短命进程），所以不做缓存。 */
public class ExecutorRuntimeQueryService {

    private final ExecutorRuntimeProbe probe;

    public ExecutorRuntimeQueryService(ExecutorRuntimeProbe probe) {
        this.probe = probe;
    }

    public ExecutorRuntimeStatus check() {
        return probe.check();
    }
}
