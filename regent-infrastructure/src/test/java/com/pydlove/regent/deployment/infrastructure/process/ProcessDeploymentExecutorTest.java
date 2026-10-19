package com.pydlove.regent.deployment.infrastructure.process;

import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
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
