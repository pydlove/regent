package com.pydlove.regent.testing.interfaces.dto;

/** 新增或编辑测试用例。 */
public record TestCaseSaveRequest(
        Long projectId,
        String name,
        String type,
        String description,
        String command,
        String scriptFilename,
        String scriptContent,
        Boolean enabled) {
}
