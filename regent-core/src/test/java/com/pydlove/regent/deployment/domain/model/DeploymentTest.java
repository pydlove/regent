package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeploymentTest {

    @Test
    @DisplayName("创建部署时规范化名称、服务器并计算脚本摘要")
    void createsDeployment() {
        DeploymentServerId serverId = DeploymentServerId.of(9L);
        Deployment deployment = Deployment.create(
                ProjectId.of(7L),
                serverId,
                "  生产 API  ",
                " /opt/regent ",
                "deploy.sh",
                "#!/usr/bin/env bash\necho ok\n");

        assertEquals("生产 API", deployment.getName());
        assertEquals(ProjectId.of(7L), deployment.getProjectId());
        assertEquals(serverId, deployment.getServerId());
        assertEquals("/opt/regent", deployment.getRemoteDirectory());
        assertNotNull(deployment.getScriptHash());
        assertEquals(64, deployment.getScriptHash().length());
    }

    @Test
    @DisplayName("远程目录必须是安全路径，拒绝相对路径和路径穿越")
    void rejectsUnsafeRemoteDirectory() {
        assertThrows(DomainException.class, () -> deployment("relative/path"));
        assertThrows(DomainException.class, () -> deployment("/opt/../secret"));
    }

    @Test
    @DisplayName("脚本文件名不能带路径分隔符")
    void rejectsInvalidFilename() {
        assertThrows(DomainException.class, () -> Deployment.create(
                ProjectId.of(7L), DeploymentServerId.of(9L),
                "生产", "/opt", "../deploy.sh", "echo ok"));
    }

    private static Deployment deployment(String remoteDirectory) {
        return Deployment.create(
                ProjectId.of(7L), DeploymentServerId.of(9L),
                "生产", remoteDirectory, "deploy.sh", "echo ok");
    }
}
