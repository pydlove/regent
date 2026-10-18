package com.pydlove.regent.testing.interfaces.dto;

import java.util.List;

/** 从测试用例菜单批量手动执行。 */
public record RunTestCasesRequest(List<Long> testCaseIds) {
}
