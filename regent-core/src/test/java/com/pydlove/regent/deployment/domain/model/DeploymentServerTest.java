package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 可复用服务器只保存连接信息，不持有密码或脚本。 */
class DeploymentServerTest {

    @Test
    @DisplayName("服务器规范化名称与连接信息")
    void createsServer() {
        DeploymentServer server = DeploymentServer.create(
                ProjectId.of(7L), "  生产服务器  ", " api.example.com ", 22,
                " root ", DeploymentAuthType.KEY);

        assertEquals("生产服务器", server.getName());
        assertEquals("api.example.com", server.getHost());
        assertEquals("root", server.getUsername());
        assertEquals(ProjectId.of(7L), server.getProjectId());
    }

    @Test
    @DisplayName("服务器拒绝非法端口与主机")
    void rejectsInvalidConnection() {
        assertThrows(DomainException.class, () -> DeploymentServer.create(
                ProjectId.of(7L), "生产", "host", 0, "root", DeploymentAuthType.KEY));
        assertThrows(DomainException.class, () -> DeploymentServer.create(
                ProjectId.of(7L), "生产", "bad host", 22, "root", DeploymentAuthType.KEY));
    }
}
