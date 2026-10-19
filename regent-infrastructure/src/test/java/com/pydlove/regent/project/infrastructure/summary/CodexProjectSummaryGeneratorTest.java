package com.pydlove.regent.project.infrastructure.summary;

import com.pydlove.regent.execution.domain.model.ExecRequest;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.port.AgentExecutor;
import com.pydlove.regent.execution.domain.port.RunEventListener;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectSummaryRequest;
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

class CodexProjectSummaryGeneratorTest {

    private final AtomicReference<ExecRequest> captured = new AtomicReference<>();

    @Test
    @DisplayName("项目资料生成以只读模式调用 Codex，并把 summary 作为资料返回")
    void generatesSummaryReadOnly() {
        AgentExecutor executor = result(new ExecResult(
                RunStatus.SUCCESS, 0, "th-1", TokenUsage.ZERO,
                "# 项目资料\n\nJava 17", "{}", true, List.of(), List.of(), null));

        String summary = new CodexProjectSummaryGenerator(executor, Duration.ofMinutes(3))
                .generate(request());

        assertEquals("# 项目资料\n\nJava 17", summary);
        assertEquals(ProjectId.of(7L), captured.get().projectId());
        assertTrue(captured.get().isReadOnly(), "项目资料只能分析，不能获得工作区写权限");
        assertTrue(captured.get().prompt().contains("只读分析当前项目"));
        assertTrue(captured.get().prompt().contains("不得修改任何文件"));
    }

    @Test
    @DisplayName("Codex 报告有改动文件时拒绝结果，避免把越权写入当成项目资料")
    void rejectsUnexpectedChanges() {
        AgentExecutor executor = result(new ExecResult(
                RunStatus.SUCCESS, 0, "th-1", TokenUsage.ZERO,
                "资料", "{}", true, List.of("README.md"), List.of(), null));

        DomainException error = assertThrows(DomainException.class,
                () -> new CodexProjectSummaryGenerator(executor, Duration.ofMinutes(3))
                        .generate(request()));

        assertTrue(error.getMessage().contains("改动文件"));
    }

    private AgentExecutor result(ExecResult result) {
        return (request, listener) -> {
            captured.set(request);
            return result;
        };
    }

    private static ProjectSummaryRequest request() {
        return new ProjectSummaryRequest(
                ProjectId.of(7L), "regent", "需求执行平台",
                "/tmp/regent", "Java 17 / Maven", "mvn test");
    }
}
