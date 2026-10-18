package com.pydlove.regent.testing.application.command;

/** 新增或编辑测试用例的入参。 */
public record SaveTestCaseCommand(
        Long projectId,
        String name,
        String type,
        String description,
        String command,
        String scriptFilename,
        String scriptContent,
        Boolean enabled) {
}
