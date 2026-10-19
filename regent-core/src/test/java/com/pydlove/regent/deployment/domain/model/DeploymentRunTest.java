package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentRunTest {

    @Test
    @DisplayName("部署记录从排队开始，成功后收敛为终态")
    void runsThroughSuccess() {
        DeploymentRun run = DeploymentRun.queue(deploymentSnapshot(), serverSnapshot());
        assertEquals(DeploymentRunStatus.QUEUED, run.getStatus());

        run.begin();
        assertEquals(DeploymentRunStatus.RUNNING, run.getStatus());

        run.finish(new DeploymentExecutionResult(DeploymentRunStatus.SUCCESS, 0, "部署完成"));
        assertEquals(DeploymentRunStatus.SUCCESS, run.getStatus());
        assertEquals(0, run.getExitCode());
        assertTrue(run.getStatus().isFinished());
    }

    @Test
    @DisplayName("中断可以把未收场的部署收敛掉")
    void interruptsActiveRun() {
        DeploymentRun run = DeploymentRun.queue(deploymentSnapshot(), serverSnapshot());
        run.begin();
        run.interrupt("平台重启");
        assertEquals(DeploymentRunStatus.INTERRUPTED, run.getStatus());
        assertTrue(run.getFinishedAt() != null);
    }

    private static DeploymentSnapshot deploymentSnapshot() {
        Deployment deployment = Deployment.create(
                ProjectId.of(7L), DeploymentServerId.of(9L), "生产",
                "/opt/app", "deploy.sh", "echo ok");
        deployment.assignIdentity(DeploymentId.of(1L));
        return deployment.snapshot();
    }

    private static DeploymentServerSnapshot serverSnapshot() {
        DeploymentServer server = DeploymentServer.create(
                ProjectId.of(7L), "生产服务器", "api.example.com", 22,
                "root", DeploymentAuthType.KEY);
        server.assignIdentity(DeploymentServerId.of(9L));
        return server.snapshot();
    }
}
