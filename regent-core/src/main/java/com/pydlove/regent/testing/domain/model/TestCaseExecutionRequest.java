package com.pydlove.regent.testing.domain.model;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.shared.domain.DomainException;

import java.util.Map;
import java.util.Objects;

/** 执行器需要的完整上下文。测试进程与 codex 共用项目目录和项目环境信息。 */
public record TestCaseExecutionRequest(
        TaskId taskId,
        TestCaseId testCaseId,
        TestCaseType type,
        String command,
        String scriptFilename,
        String scriptContent,
        String projectPath,
        Map<String, String> environment) {

    public TestCaseExecutionRequest {
        Objects.requireNonNull(testCaseId, "测试用例 ID 不能为空");
        Objects.requireNonNull(type, "测试类型不能为空");
        if ((command == null || command.isBlank())
                && (scriptContent == null || scriptContent.isBlank())) {
            throw new DomainException("测试执行缺少命令与脚本");
        }
        if (projectPath == null || projectPath.isBlank()) {
            throw new DomainException("项目路径不能为空");
        }
        environment = environment == null ? Map.of() : Map.copyOf(environment);
    }
}
