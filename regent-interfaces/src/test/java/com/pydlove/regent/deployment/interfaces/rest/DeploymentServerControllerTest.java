package com.pydlove.regent.deployment.interfaces.rest;

import com.pydlove.regent.deployment.application.command.DeploymentServerCommandService;
import com.pydlove.regent.deployment.application.command.SaveDeploymentServerCommand;
import com.pydlove.regent.deployment.application.query.DeploymentServerOverview;
import com.pydlove.regent.deployment.application.query.DeploymentServerQueryService;
import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.deployment.domain.model.DeploymentServer;
import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.query.ProjectAccessService;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.support.TestCurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeploymentServerControllerTest {

    private RecordingCommandService commandService;
    private RecordingProjectAccess projectAccess;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        commandService = new RecordingCommandService();
        projectAccess = new RecordingProjectAccess();
        mockMvc = MockMvcBuilders.standaloneSetup(new DeploymentServerController(
                        commandService,
                        new StubQueryService(commandService),
                        projectAccess))
                .addInterceptors(TestCurrentUser.interceptor())
                .build();
    }

    @Test
    @DisplayName("新建服务器接受多个归属项目并逐个校验权限")
    void createsServerForMultipleProjects() throws Exception {
        mockMvc.perform(post("/api/deployment-servers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectIds": [7, 8],
                                  "name": "共享服务器",
                                  "host": "api.example.com",
                                  "port": 22,
                                  "username": "root",
                                  "authType": "KEY",
                                  "defaultRemoteDirectory": "/srv/app"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectIds[0]").value(7))
                .andExpect(jsonPath("$.projectIds[1]").value(8));

        assertEquals(
                List.of(ProjectId.of(7L), ProjectId.of(8L)),
                commandService.lastCommand.projectIds());
        assertEquals(
                List.of(ProjectId.of(7L), ProjectId.of(8L)),
                projectAccess.requiredProjects);
    }

    @Test
    @DisplayName("编辑服务器可以替换归属项目")
    void updatesProjectAssignments() throws Exception {
        commandService.server = server(List.of(ProjectId.of(7L)));

        mockMvc.perform(put("/api/deployment-servers/31")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectIds": [8, 9],
                                  "name": "共享服务器",
                                  "host": "api.example.com",
                                  "port": 22,
                                  "username": "root",
                                  "authType": "KEY",
                                  "defaultRemoteDirectory": "/srv/app"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectIds[0]").value(8))
                .andExpect(jsonPath("$.projectIds[1]").value(9));

        assertEquals(
                List.of(ProjectId.of(8L), ProjectId.of(9L)),
                commandService.server.getProjectIds());
        assertEquals(
                List.of(ProjectId.of(8L), ProjectId.of(9L)),
                projectAccess.requiredProjects);
    }

    private static DeploymentServer server(List<ProjectId> projectIds) {
        return DeploymentServer.restore(
                DeploymentServerId.of(31L),
                projectIds,
                "共享服务器",
                "api.example.com",
                22,
                "root",
                DeploymentAuthType.KEY,
                "/srv/app",
                Instant.parse("2026-10-20T00:00:00Z"),
                Instant.parse("2026-10-20T00:00:00Z"));
    }

    private static final class RecordingCommandService extends DeploymentServerCommandService {

        private DeploymentServer server;
        private SaveDeploymentServerCommand lastCommand;

        private RecordingCommandService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public DeploymentServer create(SaveDeploymentServerCommand command) {
            lastCommand = command;
            server = DeploymentServer.create(
                    command.projectIds(),
                    command.name(),
                    command.host(),
                    command.port(),
                    command.username(),
                    command.authType(),
                    command.defaultRemoteDirectory());
            server.assignIdentity(DeploymentServerId.of(31L));
            return server;
        }

        @Override
        public DeploymentServer update(DeploymentServerId id,
                                       SaveDeploymentServerCommand command) {
            lastCommand = command;
            server.update(
                    command.projectIds(),
                    command.name(),
                    command.host(),
                    command.port(),
                    command.username(),
                    command.authType(),
                    command.defaultRemoteDirectory());
            return server;
        }
    }

    private static final class StubQueryService extends DeploymentServerQueryService {

        private final RecordingCommandService commandService;

        private StubQueryService(RecordingCommandService commandService) {
            super(null, null, null);
            this.commandService = commandService;
        }

        @Override
        public DeploymentServerOverview get(DeploymentServerId id, UserId ownerId) {
            return new DeploymentServerOverview(commandService.server, true, 0);
        }
    }

    private static final class RecordingProjectAccess extends ProjectAccessService {

        private final List<ProjectId> requiredProjects = new ArrayList<>();

        private RecordingProjectAccess() {
            super(null);
        }

        @Override
        public ProjectProfile require(ProjectId projectId, UserId ownerId) {
            requiredProjects.add(projectId);
            return null;
        }
    }
}
