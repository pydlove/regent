package com.pydlove.regent.testing.domain.port;

import com.pydlove.regent.testing.domain.model.TestCaseExecutionRequest;
import com.pydlove.regent.testing.domain.model.TestCaseExecutionResult;

/** 测试进程执行端口。具体用 shell、容器还是远程执行器由基础设施决定。 */
public interface TestCaseExecutor {

    TestCaseExecutionResult execute(TestCaseExecutionRequest request);
}
