package com.pydlove.regent.testing.interfaces.dto;

import java.util.List;

/** 整体替换任务绑定的测试用例。 */
public record BindTaskTestCasesRequest(List<Long> testCaseIds) {
}
