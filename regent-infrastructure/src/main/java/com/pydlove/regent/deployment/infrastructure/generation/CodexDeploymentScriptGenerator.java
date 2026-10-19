package com.pydlove.regent.deployment.infrastructure.generation;

import com.pydlove.regent.deployment.domain.model.DeploymentScriptGenerationRequest;
import com.pydlove.regent.deployment.domain.port.DeploymentScriptGenerator;
import com.pydlove.regent.execution.domain.model.AgentAccessMode;
import com.pydlove.regent.execution.domain.model.ExecRequest;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.port.AgentExecutor;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 用 Codex 只读分析项目，并生成符合平台契约的远程部署脚本。 */
public class CodexDeploymentScriptGenerator implements DeploymentScriptGenerator {

    private static final int MAX_PROJECT_SUMMARY_CHARS = 24_000;
    private static final Pattern FENCED_SCRIPT = Pattern.compile(
            "(?is)```(?:bash|sh|shell)?\\s*\\R(.*?)```");

    private final AgentExecutor agentExecutor;
    private final Duration timeout;

    public CodexDeploymentScriptGenerator(AgentExecutor agentExecutor, Duration timeout) {
        this.agentExecutor = agentExecutor;
        this.timeout = timeout;
    }

    @Override
    public String generate(DeploymentScriptGenerationRequest request) {
        ExecResult result = agentExecutor.execute(new ExecRequest(
                request.projectId(),
                request.workspacePath(),
                prompt(request),
                null,
                timeout,
                List.of(),
                Map.of(),
                null,
                AgentAccessMode.READ_ONLY), (type, payload) -> {
        });

        if (!result.schemaValid()) {
            throw new DomainException("Codex 部署脚本出参不符合约定：" + nonBlank(result.summary()));
        }
        if (result.status() != RunStatus.SUCCESS) {
            throw new DomainException("Codex 部署脚本生成未完成（" + result.status() + "）："
                    + nonBlank(result.summary()));
        }
        if (!result.changedFiles().isEmpty()) {
            throw new DomainException("脚本生成过程改动了项目文件，结果不可信：" + result.changedFiles());
        }
        if (result.decision() != null
                || result.branchIntent() != null && result.branchIntent().requested()) {
            throw new DomainException("脚本生成不应请求人工决策或隔离分支");
        }
        return extractScript(result.summary());
    }

    static String extractScript(String summary) {
        if (summary == null || summary.isBlank()) {
            throw new DomainException("Codex 未返回部署脚本");
        }
        String text = summary.trim();
        Matcher fenced = FENCED_SCRIPT.matcher(text);
        while (fenced.find()) {
            String candidate = fenced.group(1).strip();
            if (candidate.startsWith("#!")) return candidate + "\n";
        }
        if (text.startsWith("#!")) return text + "\n";

        int shebang = text.indexOf("#!");
        if (shebang >= 0) {
            return text.substring(shebang).strip() + "\n";
        }
        throw new DomainException("Codex 返回的内容不是 shell 脚本");
    }

    private static String prompt(DeploymentScriptGenerationRequest request) {
        return """
                只读分析当前项目，为平台的远程部署功能生成一份可直接执行的 bash 脚本。

                项目标识：%s
                项目名称：%s
                项目根目录：%s
                本次分析目录：%s
                已知技术栈：%s
                已知验证方式：%s
                目标服务器：%s（%s@%s:%d）
                远程目录：%s

                项目资料：
                %s

                生成要求：
                1. 必须保持只读，不得修改项目文件，不得创建、切换或删除 Git 分支。
                2. 脚本会在目标服务器上执行，平台先把工作目录切到 REGENT_DEPLOY_DIRECTORY，再通过 bash 标准输入执行脚本。
                3. 第一行必须是 #!/usr/bin/env bash，紧接着启用 set -Eeuo pipefail。
                4. 使用 "$REGENT_DEPLOY_DIRECTORY" 作为远程目录，不得写死 cd /...；不得执行 ssh、scp 或 sftp。
                5. 不得接收主机、端口、用户名或密码；这些连接信息由平台负责。
                6. 脚本必须完全非交互，不得使用 read -p；需要 sudo 时只能使用 sudo -n，并在失败时明确提示。
                7. 生成前优先查找并阅读项目已有的部署脚本，重点检查 deploy/、script/、scripts/ 目录以及根目录下的 deploy*.sh。
                   参考 deploy_aisg.sh、delpoy_*.sh 这类真实脚本，保留其中的服务名、JAR、配置目录、thirdparty、
                   备份命名、原子替换、权限修正和重启检查等业务步骤。
                8. 已有脚本中的 SSH、scp、sftp、sshpass、主机、端口、用户名、密码和写死远程目录必须移除，
                   改写成平台提供的 REGENT_DEPLOY_DIRECTORY 和服务器上下文；不要机械复制违规部分。
                9. 根据项目的构建文件、部署产物和运行方式生成实际步骤；平台不会上传本地构建产物，所以脚本必须检查远端所需文件或命令是否存在，缺失时给出清晰错误并非零退出。
                10. 脚本应包含必要的预检、备份、替换或安装、权限修正和重启/启动步骤；无法可靠推断的命令不要凭空编造。
                11. 多个已有脚本并存时，选择与当前项目文件夹、技术栈和远程目录最接近的一份作为主参考，其他脚本只用于补充。
                12. 不输出任何密钥、令牌、密码或环境变量值。
                13. 把完整脚本原样写入结构化出参的 summary，不要 Markdown 围栏或解释文字；
                    status 必须是 DONE，changedFiles 和 bugs 为空数组，branch 与 decision 必须是 null。
                """.formatted(
                request.projectCode(),
                request.projectName(),
                request.projectPath(),
                request.workspacePath(),
                textOrUnknown(request.techStack()),
                textOrUnknown(request.verifyMethod()),
                request.serverName(),
                request.serverUsername(),
                request.serverHost(),
                request.serverPort(),
                request.remoteDirectory(),
                limited(request.projectSummary()));
    }

    private static String limited(String projectSummary) {
        if (projectSummary == null || projectSummary.isBlank()) {
            return "未生成项目资料，请直接读取项目文件核实。";
        }
        String value = projectSummary.strip();
        return value.length() <= MAX_PROJECT_SUMMARY_CHARS
                ? value
                : value.substring(0, MAX_PROJECT_SUMMARY_CHARS) + "\n（项目资料已截断）";
    }

    private static String textOrUnknown(String value) {
        return value == null || value.isBlank() ? "未知，请从项目文件核实" : value;
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? "未返回原因" : value;
    }
}
