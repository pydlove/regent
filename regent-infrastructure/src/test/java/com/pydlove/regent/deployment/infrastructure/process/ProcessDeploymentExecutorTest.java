package com.pydlove.regent.deployment.infrastructure.process;

import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionMode;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionRequest;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionResult;
import com.pydlove.regent.deployment.domain.model.DeploymentRunId;
import com.pydlove.regent.deployment.domain.model.DeploymentRunStatus;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessDeploymentExecutorTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("项目发布脚本在项目目录本机执行，连接参数走 argv，密码只走环境变量")
    void executesProjectReleaseScriptLocally() throws Exception {
        Path project = temp.resolve("project");
        Path script = project.resolve("deploy/remote/deploy.sh");
        Files.createDirectories(script.getParent());
        String content = """
                #!/usr/bin/env bash
                set -Eeuo pipefail
                printf '%s\\n' "$@" > args.txt
                printf '%s' "$REGENT_SERVER_PASSWORD" > password.txt
                printf '%s' "$REGENT_REMOTE_BASE" > remote-base.txt
                echo "project release output"
                """;
        Files.writeString(script, content, StandardCharsets.UTF_8);

        ProcessDeploymentExecutor executor = new ProcessDeploymentExecutor(
                temp.resolve("ssh-must-not-run").toString(), Duration.ofSeconds(5));
        StringBuilder log = new StringBuilder();

        DeploymentExecutionResult result = executor.execute(new DeploymentExecutionRequest(
                DeploymentRunId.of(42L),
                ProjectId.of(7L),
                "api.example.com",
                2222,
                "deploy",
                DeploymentAuthType.PASSWORD,
                "/root/app",
                "deploy.sh",
                content,
                DeploymentExecutionMode.PROJECT_SCRIPT,
                "deploy/remote/deploy.sh",
                "s3cret",
                project.toString()), log::append);

        assertEquals(DeploymentRunStatus.SUCCESS, result.status());
        assertTrue(log.toString().contains("project release output"));
        assertTrue(log.toString().contains("mvn="));
        assertTrue(log.toString().contains("npm="));
        assertTrue(log.toString().contains("node="));
        assertEquals(List.of(
                "-H", "api.example.com",
                "-P", "2222",
                "-u", "deploy",
                "-r", "/root/app"), Files.readAllLines(project.resolve("args.txt"), StandardCharsets.UTF_8));
        assertEquals("s3cret", Files.readString(project.resolve("password.txt"), StandardCharsets.UTF_8));
        assertEquals("/root/app", Files.readString(
                project.resolve("remote-base.txt"), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("本机发布 PATH 补齐用户级 Maven、NVM 与系统工具目录")
    void enrichesLocalProcessPath() {
        String path = ProcessDeploymentExecutor.localProcessPath("/usr/bin:/bin");

        assertTrue(path.contains("/usr/local/bin"));
        assertTrue(path.endsWith("/sbin"));
        assertTrue(path.startsWith("/usr/bin:/bin"));
    }

    @Test
    @DisplayName("远端脚本通过固定环境变量拿到部署上下文，SSH 密码不进入脚本参数")
    void exposesDeploymentContextWithoutPassword() throws Exception {
        Path arguments = temp.resolve("arguments.txt");
        Path stdin = temp.resolve("stdin.txt");
        Path ssh = fakeSsh(arguments, stdin);
        ProcessDeploymentExecutor executor = new ProcessDeploymentExecutor(
                ssh.toString(), Duration.ofSeconds(5));

        DeploymentExecutionResult result = executor.execute(new DeploymentExecutionRequest(
                DeploymentRunId.of(41L),
                ProjectId.of(7L),
                "2001:db8::10",
                2222,
                "deploy",
                DeploymentAuthType.PASSWORD,
                "/opt/app release",
                "publish.sh",
                "#!/usr/bin/env bash\necho deploy\n",
                "s3cret"), ignored -> {
        });

        assertEquals(DeploymentRunStatus.SUCCESS, result.status());
        List<String> sshArguments = Files.readAllLines(arguments, StandardCharsets.UTF_8);
        String remoteCommand = sshArguments.get(sshArguments.size() - 1);
        assertTrue(remoteCommand.contains("REGENT_DEPLOY_RUN_ID='41'"));
        assertTrue(remoteCommand.contains("REGENT_DEPLOY_PROJECT_ID='7'"));
        assertTrue(remoteCommand.contains("REGENT_DEPLOY_HOST='2001:db8::10'"));
        assertTrue(remoteCommand.contains("REGENT_DEPLOY_PORT='2222'"));
        assertTrue(remoteCommand.contains("REGENT_DEPLOY_USER='deploy'"));
        assertTrue(remoteCommand.contains("REGENT_DEPLOY_AUTH_TYPE='PASSWORD'"));
        assertTrue(remoteCommand.contains("REGENT_DEPLOY_DIRECTORY=\"$(pwd -P)\""));
        assertTrue(remoteCommand.contains("REGENT_DEPLOY_SCRIPT='publish.sh'"));
        assertTrue(remoteCommand.contains("cd '/opt/app release' && env"));
        assertTrue(remoteCommand.endsWith(" bash -s"));
        assertFalse(remoteCommand.contains("s3cret"));
        assertEquals("#!/usr/bin/env bash\necho deploy\n\n",
                Files.readString(stdin, StandardCharsets.UTF_8));
    }

    private Path fakeSsh(Path arguments, Path stdin) throws Exception {
        Path script = temp.resolve("fake-ssh.sh");
        Files.writeString(script, """
                #!/bin/sh
                printf '%%s\n' "$@" > '%s'
                cat > '%s'
                """.formatted(arguments, stdin), StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        return script;
    }
}
