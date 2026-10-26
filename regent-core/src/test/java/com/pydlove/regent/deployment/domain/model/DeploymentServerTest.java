package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(List.of(ProjectId.of(7L)), server.getProjectIds());
    }

    @Test
    @DisplayName("服务器可以归属多个项目，并可在编辑时调整")
    void supportsMultipleProjects() {
        DeploymentServer server = DeploymentServer.create(
                List.of(ProjectId.of(7L), ProjectId.of(8L)),
                "共享服务器", "api.example.com", 22, "root", DeploymentAuthType.KEY);

        assertTrue(server.belongsTo(ProjectId.of(7L)));
        assertTrue(server.belongsTo(ProjectId.of(8L)));
        assertFalse(server.belongsTo(ProjectId.of(9L)));

        server.update(
                List.of(ProjectId.of(8L), ProjectId.of(9L)),
                "共享服务器", "api.example.com", 22, "root", DeploymentAuthType.KEY, null);

        assertEquals(List.of(ProjectId.of(8L), ProjectId.of(9L)), server.getProjectIds());
        assertFalse(server.belongsTo(ProjectId.of(7L)));
    }

    @Test
    @DisplayName("服务器至少归属一个项目且项目不能重复")
    void rejectsMissingOrDuplicateProjects() {
        assertThrows(DomainException.class, () -> DeploymentServer.create(
                List.of(), "生产", "api.example.com", 22, "root", DeploymentAuthType.KEY));
        assertThrows(DomainException.class, () -> DeploymentServer.create(
                List.of(ProjectId.of(7L), ProjectId.of(7L)),
                "生产", "api.example.com", 22, "root", DeploymentAuthType.KEY));
    }

    @Test
    @DisplayName("服务器拒绝非法端口与主机")
    void rejectsInvalidConnection() {
        assertThrows(DomainException.class, () -> DeploymentServer.create(
                ProjectId.of(7L), "生产", "host", 0, "root", DeploymentAuthType.KEY));
        assertThrows(DomainException.class, () -> DeploymentServer.create(
                ProjectId.of(7L), "生产", "bad host", 22, "root", DeploymentAuthType.KEY));
    }

    @Test
    @DisplayName("默认远程目录只接受绝对路径或 ~，并拒绝路径穿越")
    void validatesDefaultRemoteDirectory() {
        DeploymentServer server = DeploymentServer.create(
                ProjectId.of(7L), "生产", "api.example.com", 22, "root",
                DeploymentAuthType.KEY, "/srv/app-audit-frontend");
        assertEquals("/srv/app-audit-frontend", server.getDefaultRemoteDirectory());

        assertThrows(DomainException.class, () -> DeploymentServer.create(
                ProjectId.of(7L), "生产", "api.example.com", 22, "root",
                DeploymentAuthType.KEY, "relative/path"));
        assertThrows(DomainException.class, () -> DeploymentServer.create(
                ProjectId.of(7L), "生产", "api.example.com", 22, "root",
                DeploymentAuthType.KEY, "/srv/../etc"));
    }
}
