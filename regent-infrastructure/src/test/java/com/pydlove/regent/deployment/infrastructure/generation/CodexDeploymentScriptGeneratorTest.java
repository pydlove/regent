package com.pydlove.regent.deployment.infrastructure.generation;

import com.pydlove.regent.deployment.domain.model.DeploymentScriptGenerationRequest;
import com.pydlove.regent.execution.domain.model.ExecRequest;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.port.AgentExecutor;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.TokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexDeploymentScriptGeneratorTest {

    private final AtomicReference<ExecRequest> captured = new AtomicReference<>();

    @Test
    @DisplayName("只读分析项目并从 Markdown 代码围栏中提取脚本")
    void generatesScriptReadOnly() {
        AgentExecutor executor = result("""
                下面是一份脚本：
                ```bash
                #!/usr/bin/env bash
                set -Eeuo pipefail
                echo deploy
                ```
                """);

        String script = new CodexDeploymentScriptGenerator(executor, Duration.ofMinutes(3))
                .generate(request());

        assertTrue(script.startsWith("#!/usr/bin/env bash"));
        assertTrue(script.contains("set -Eeuo pipefail"));
        assertTrue(captured.get().isReadOnly());
        assertEquals("/opt/regent/service", captured.get().projectPath());
        assertTrue(captured.get().prompt().contains("/srv/regent"));
        assertTrue(captured.get().prompt().contains("平台不会上传本地构建产物"));
        assertTrue(captured.get().prompt().contains("项目已有的部署脚本"));
        assertTrue(captured.get().prompt().contains("deploy_aisg.sh"));
    }

    @Test
    @DisplayName("生成过程改动文件时拒绝结果")
    void rejectsUnexpectedWorkspaceChanges() {
        AgentExecutor executor = (request, listener) -> new ExecResult(
                RunStatus.SUCCESS, 0, "th-1", TokenUsage.ZERO,
                validScript(), "{}", true, List.of("pom.xml"), List.of(), null);

        DomainException error = assertThrows(DomainException.class,
                () -> new CodexDeploymentScriptGenerator(executor, Duration.ofMinutes(3))
                        .generate(request()));

        assertTrue(error.getMessage().contains("改动了项目文件"));
    }

    @Test
    @DisplayName("没有 shell 脚本内容时拒绝空泛输出")
    void rejectsNonScriptSummary() {
        AgentExecutor executor = result("建议先检查服务器环境。");

        DomainException error = assertThrows(DomainException.class,
                () -> new CodexDeploymentScriptGenerator(executor, Duration.ofMinutes(3))
                        .generate(request()));

        assertTrue(error.getMessage().contains("不是 shell 脚本"));
    }

    private AgentExecutor result(String summary) {
        return (request, listener) -> {
            captured.set(request);
            return new ExecResult(
                    RunStatus.SUCCESS, 0, "th-1", TokenUsage.ZERO,
                    summary, "{}", true, List.of(), List.of(), null);
        };
    }

    private static DeploymentScriptGenerationRequest request() {
        return new DeploymentScriptGenerationRequest(
                ProjectId.of(7L),
                "regent",
                "Regent",
                "/opt/regent",
                "Java 17 / Maven",
                "mvn test",
                "# 项目资料",
                "/opt/regent/service",
                "生产服务器",
                "api.example.com",
                22,
                "root",
                "/srv/regent");
    }

    private static String validScript() {
        return """
                #!/usr/bin/env bash
                set -Eeuo pipefail
                echo deploy
                """;
    }
}
