package com.pydlove.regent.testing.application.query;

import com.pydlove.regent.testing.domain.model.TestCase;
import com.pydlove.regent.testing.domain.model.TestCaseRun;

/** 测试用例列表投影：配置 + 最近一次执行结果。 */
public record TestCaseOverview(TestCase testCase, String projectName, TestCaseRun latestRun) {
}
