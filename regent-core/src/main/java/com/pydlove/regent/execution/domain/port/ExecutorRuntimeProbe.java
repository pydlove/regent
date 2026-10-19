package com.pydlove.regent.execution.domain.port;

import com.pydlove.regent.execution.domain.model.ExecutorRuntimeStatus;

/** 检查本机执行器是否安装且能够启动。 */
public interface ExecutorRuntimeProbe {

    ExecutorRuntimeStatus check();
}
