package com.pydlove.regent.project.infrastructure.summary;

import com.pydlove.regent.execution.domain.model.AgentAccessMode;
import com.pydlove.regent.execution.domain.model.ExecRequest;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.port.AgentExecutor;
import com.pydlove.regent.project.domain.model.ProjectSummaryRequest;
import com.pydlove.regent.project.domain.port.ProjectSummaryGenerator;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Duration;
import java.util.List;

/**
 * 用 Codex 只读分析项目并生成一份 Markdown 资料。
 *
 * <p>复用任务执行适配器的进程管理、超时与结构化出参能力；差异只在指令和访问级别。
 * 生成失败统一抛出领域异常，由应用层写成可重试的项目资料失败状态。
 */
public class CodexProjectSummaryGenerator implements ProjectSummaryGenerator {

    private final AgentExecutor agentExecutor;
    private final Duration timeout;

    public CodexProjectSummaryGenerator(AgentExecutor agentExecutor, Duration timeout) {
        this.agentExecutor = agentExecutor;
        this.timeout = timeout;
    }

    @Override
    public String generate(ProjectSummaryRequest request) {
        ExecResult result = agentExecutor.execute(new ExecRequest(
                request.projectId(),
                request.path(),
                prompt(request),
                null,
                timeout,
                List.of(),
                java.util.Map.of(),
                null,
                AgentAccessMode.READ_ONLY), (type, payload) -> {
        });

        if (!result.schemaValid()) {
            throw new DomainException("Codex 项目资料出参不符合约定：" + nonBlank(result.summary()));
        }
        if (result.status() != RunStatus.SUCCESS) {
            throw new DomainException("Codex 项目资料生成未完成（" + result.status() + "）："
                    + nonBlank(result.summary()));
        }
        if (!result.changedFiles().isEmpty()) {
            throw new DomainException("只读生成返回了改动文件，结果不可信：" + result.changedFiles());
        }
        if (result.decision() != null
                || result.branchIntent() != null && result.branchIntent().requested()) {
            throw new DomainException("项目资料生成不应请求人工决策或隔离分支");
        }
        if (result.summary() == null || result.summary().isBlank()) {
            throw new DomainException("Codex 未返回项目资料");
        }
        return result.summary().trim();
    }

    private static String prompt(ProjectSummaryRequest request) {
        return """
                只读分析当前项目，并生成一份供后续需求拆解和人工理解使用的项目资料。

                项目标识：%s
                项目名称：%s
                已知技术栈：%s
                已知验证方式：%s

                要求：
                1. 必须保持只读，不得修改任何文件，不得创建、切换、重命名或删除 Git 分支。
                2. 优先依据 README、构建文件、目录结构、配置和测试代码核实现状，不猜测。
                3. 资料使用 Markdown，包含项目定位、主要技术栈、模块与目录、启动方式、验证方式、关键约定和主要风险。
                4. 不输出密钥、令牌、密码或环境变量值；发现敏感信息时只说明文件位置和风险。
                5. 把完整资料写入结构化出参的 summary；status 必须是 DONE，changedFiles 和 bugs 为空数组，
                   branch 与 decision 必须是 null。
                """.formatted(
                request.code(),
                request.name(),
                textOrUnknown(request.techStack()),
                textOrUnknown(request.verifyMethod()));
    }

    private static String textOrUnknown(String value) {
        return value == null || value.isBlank() ? "未知，请从项目文件核实" : value;
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? "未返回原因" : value;
    }
}
