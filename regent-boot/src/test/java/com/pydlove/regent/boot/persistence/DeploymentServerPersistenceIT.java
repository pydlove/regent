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

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 服务器抽取、复用与删除守卫的真实数据库集成测试。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeploymentServerPersistenceIT {

    private static final long PROJECT_ID = 9191L;

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
