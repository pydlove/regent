package com.pydlove.regent.deployment.application.query;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.deployment.domain.model.DeploymentServer;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.support.DeploymentFakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeploymentServerQueryServiceTest {

    @Test
    @DisplayName("服务器读模型只统计仍有效的关联部署")
    void countsOnlyCurrentDeployments() {
        DeploymentFakes.Servers servers = new DeploymentFakes.Servers();
        DeploymentFakes.Deployments deployments = new DeploymentFakes.Deployments();
        DeploymentFakes.Credentials credentials = new DeploymentFakes.Credentials();
        DeploymentServer server = DeploymentServer.create(
                ProjectId.of(7L),
                "生产服务器",
                "api.example.com",
                22,
                "root",
                DeploymentAuthType.KEY);
        servers.save(server);

        deployments.save(Deployment.create(
                ProjectId.of(7L), server.getId(), "部署一",
                "/opt/app", "one.sh", "echo one"));
        Deployment removed = Deployment.create(
                ProjectId.of(7L), server.getId(), "部署二",
                "/opt/app", "two.sh", "echo two");
        deployments.save(removed);
        deployments.delete(removed.getId());

        DeploymentServerQueryService service = new DeploymentServerQueryService(
                servers, deployments, credentials);

        assertEquals(1, service.get(server.getId()).deploymentCount());
        assertEquals(1, service.list(ProjectId.of(7L)).get(0).deploymentCount());
    }
}
