package com.pydlove.regent.deployment.domain.service;

import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 部署脚本必须非交互，并复用平台传入的远程目录与连接信息。 */
class DeploymentScriptValidatorTest {

    @Test
    @DisplayName("规范脚本通过检测")
    void acceptsCompliantScript() {
        String script = """
                #!/usr/bin/env bash
                set -Eeuo pipefail
                cd "$REGENT_DEPLOY_DIRECTORY"
                sudo_cmd=(sudo -n)
                sudo -n systemctl restart regent
                """;

        assertTrue(DeploymentScriptValidator.validate(script).isEmpty());
        assertDoesNotThrow(() -> DeploymentScriptValidator.requireValid(script));
    }

    @Test
    @DisplayName("缺少 bash 头和 fail-fast 设置时给出行号")
    void rejectsMissingHeaderAndStrictMode() {
        var issues = DeploymentScriptValidator.validate("echo deploy\n");

        assertEquals(2, issues.size());
        assertEquals("BASH_SHEBANG", issues.get(0).code());
        assertEquals("STRICT_MODE", issues.get(1).code());
    }

    @Test
    @DisplayName("写死目录、交互输入和重复 SSH 连接都会被拒绝")
    void rejectsHardCodedAndInteractiveCommands() {
        String script = """
                #!/usr/bin/env bash
                set -euo pipefail
                cd /opt/regent
                read -p "password: " value
                sudo apt update
                ssh root@10.0.0.8
                """;

        var issues = DeploymentScriptValidator.validate(script);

        assertEquals(4, issues.size());
        assertEquals("HARD_CODED_DIRECTORY", issues.get(0).code());
        assertEquals(3, issues.get(0).line());
        assertEquals("INTERACTIVE_READ", issues.get(1).code());
        assertEquals("INTERACTIVE_SUDO", issues.get(2).code());
        assertEquals("HARD_CODED_REMOTE_CONNECTION", issues.get(3).code());
        assertThrows(DomainException.class,
                () -> DeploymentScriptValidator.requireValid(script));
    }

    @Test
    @DisplayName("受控双模式脚本允许在 run_local 中连接服务器")
    void acceptsControlledLocalMode() {
        String script = """
                #!/usr/bin/env bash
                set -Eeuo pipefail
                HOST=""
                run_local() {
                    ssh -o BatchMode=yes root@example.test "echo ok"
                    scp -q app.jar root@example.test:/tmp/app.jar
                    remote_size="$(ssh root@example.test "stat -c %s /tmp/app.jar")"
                }
                run_remote() {
                    echo remote
                }
                if [ -n "$HOST" ]; then
                    run_local
                else
                    run_remote
                fi
                """;

        assertTrue(DeploymentScriptValidator.validate(script).isEmpty());
    }

    @Test
    @DisplayName("缺少显式本地模式入口时仍拒绝 SSH")
    void rejectsLocalFunctionWithoutRemoteDispatch() {
        String script = """
                #!/usr/bin/env bash
                set -Eeuo pipefail
                run_local() {
                    ssh root@example.test "echo ok"
                }
                run_local
                """;

        var issues = DeploymentScriptValidator.validate(script);

        assertTrue(issues.stream().anyMatch(
                issue -> "HARD_CODED_REMOTE_CONNECTION".equals(issue.code())));
    }
}
