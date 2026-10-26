package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.deployment.application.command.DeploymentCommandService;
import com.pydlove.regent.deployment.application.command.DeploymentServerCommandService;
import com.pydlove.regent.deployment.application.command.SaveDeploymentCommand;
import com.pydlove.regent.deployment.application.command.SaveDeploymentServerCommand;
import com.pydlove.regent.deployment.application.query.DeploymentQueryService;
import com.pydlove.regent.deployment.application.query.DeploymentServerQueryService;
import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 服务器抽取、复用与删除守卫的真实数据库集成测试。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeploymentServerPersistenceIT {

    private static final long PROJECT_ID = 9191L;
    private static final long SECOND_PROJECT_ID = 9192L;

    @Autowired
    private DeploymentServerCommandService serverCommandService;

    @Autowired
    private DeploymentCommandService deploymentCommandService;

    @Autowired
    private DeploymentQueryService deploymentQueryService;

    @Autowired
    private DeploymentServerQueryService serverQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 准备项目() {
        jdbcTemplate.update("""
                INSERT INTO project
                    (id, code, name, path, default_decision_level, enabled, deleted, created_at, updated_at)
                VALUES (?, 'deployment_server_it', '服务器复用测试', '/tmp/deployment-server-it',
                        'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE name = VALUES(name), deleted = 0
                """, PROJECT_ID);
        jdbcTemplate.update("""
                INSERT INTO project
                    (id, code, name, path, default_decision_level, enabled, deleted, created_at, updated_at)
                VALUES (?, 'deployment_server_it_second', '共享服务器第二项目', '/tmp/deployment-server-it-second',
                        'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE name = VALUES(name), deleted = 0
                """, SECOND_PROJECT_ID);
    }

    @Test
    @DisplayName("服务器保存多个归属项目与默认远程目录并在查询时返回")
    void 保存默认远程目录() {
        var server = serverCommandService.create(new SaveDeploymentServerCommand(
                List.of(ProjectId.of(PROJECT_ID), ProjectId.of(SECOND_PROJECT_ID)),
                "前端构建服务器",
                "api.example.com",
                22,
                "root",
                DeploymentAuthType.KEY,
                "/srv/app-audit-frontend",
                null));

        assertEquals("/srv/app-audit-frontend", server.getDefaultRemoteDirectory());
        var overview = serverQueryService.get(server.getId());
        assertEquals("/srv/app-audit-frontend",
                overview.server().getDefaultRemoteDirectory());
        assertEquals(
                List.of(ProjectId.of(PROJECT_ID), ProjectId.of(SECOND_PROJECT_ID)),
                overview.server().getProjectIds());
        assertEquals(1, serverQueryService.list(ProjectId.of(PROJECT_ID)).size());
        assertEquals(1, serverQueryService.list(ProjectId.of(SECOND_PROJECT_ID)).size());
    }

    @Test
    @DisplayName("部署可以同时归属多个项目并按任一项目筛选")
    void 保存多项目部署() {
        var server = serverCommandService.create(new SaveDeploymentServerCommand(
                List.of(ProjectId.of(PROJECT_ID), ProjectId.of(SECOND_PROJECT_ID)),
                "共享发布服务器",
                "api.example.com",
                22,
                "root",
                DeploymentAuthType.KEY,
                "/srv/shared",
                null));

        var deployment = deploymentCommandService.create(new SaveDeploymentCommand(
                List.of(ProjectId.of(PROJECT_ID), ProjectId.of(SECOND_PROJECT_ID)),
                server.getId(),
                "多项目发布",
                "/srv/shared",
                "deploy.sh",
                validScript("shared")));

        assertEquals(
                List.of(ProjectId.of(PROJECT_ID), ProjectId.of(SECOND_PROJECT_ID)),
                deployment.getProjectIds());
        assertEquals(1, deploymentQueryService.list(ProjectId.of(PROJECT_ID)).size());
        assertEquals(1, deploymentQueryService.list(ProjectId.of(SECOND_PROJECT_ID)).size());
        assertEquals(
                List.of(ProjectId.of(PROJECT_ID), ProjectId.of(SECOND_PROJECT_ID)),
                deploymentQueryService.get(deployment.getId()).deployment().getProjectIds());
    }

    @Test
    @DisplayName("编辑部署不会改变按创建时间倒序的列表位置")
    void 编辑后保持创建时间顺序() {
        var server = serverCommandService.create(new SaveDeploymentServerCommand(
                ProjectId.of(PROJECT_ID),
                "顺序测试服务器",
                "api.example.com",
                22,
                "root",
                DeploymentAuthType.KEY,
                null));

        var first = deploymentCommandService.create(new SaveDeploymentCommand(
                ProjectId.of(PROJECT_ID),
                server.getId(),
                "先创建的部署",
                "/opt/first",
                "deploy-first.sh",
                validScript("first")));
        deploymentCommandService.create(new SaveDeploymentCommand(
                ProjectId.of(PROJECT_ID),
                server.getId(),
                "后创建的部署",
                "/opt/second",
                "deploy-second.sh",
                validScript("second")));

        deploymentCommandService.update(first.getId(), new SaveDeploymentCommand(
                ProjectId.of(PROJECT_ID),
                server.getId(),
                "先创建的部署（已编辑）",
                "/opt/first",
                "deploy-first.sh",
                validScript("first edited")));

        var deployments = deploymentQueryService.list(ProjectId.of(PROJECT_ID));
        assertEquals(
                List.of("后创建的部署", "先创建的部署（已编辑）"),
                deployments.stream().map(item -> item.deployment().getName()).toList());
    }

    @Test
    @DisplayName("删除服务器时同时移除关联部署配置")
    void 删除服务器时同时移除关联部署配置() {
        var server = serverCommandService.create(new SaveDeploymentServerCommand(
                ProjectId.of(PROJECT_ID),
                "生产服务器",
                "api.example.com",
                22,
                "root",
                DeploymentAuthType.KEY,
                null));

        deploymentCommandService.create(new SaveDeploymentCommand(
                ProjectId.of(PROJECT_ID),
                server.getId(),
                "HTTP 部署",
                "/opt/http",
                "deploy-http.sh",
                validScript("http")));
        deploymentCommandService.create(new SaveDeploymentCommand(
                ProjectId.of(PROJECT_ID),
                server.getId(),
                "AISG 部署",
                "/opt/aisg",
                "deploy-aisg.sh",
                validScript("aisg")));

        var deployments = deploymentQueryService.list(ProjectId.of(PROJECT_ID));
        assertEquals(2, deployments.size());
        assertEquals(server.getId(), deployments.get(0).deployment().getServerId());
        assertEquals("生产服务器", deployments.get(0).server().getName());

        serverCommandService.delete(server.getId());

        assertEquals(0, serverQueryService.list(ProjectId.of(PROJECT_ID)).size());
        assertEquals(0, deploymentQueryService.list(ProjectId.of(PROJECT_ID)).size());
    }

    private static String validScript(String message) {
        return """
                #!/usr/bin/env bash
                set -Eeuo pipefail
                echo %s
                """.formatted(message);
    }
}
