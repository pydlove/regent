package com.pydlove.regent.deployment.interfaces.rest;

import com.pydlove.regent.deployment.application.command.DeploymentCommandService;
import com.pydlove.regent.deployment.application.command.DeploymentScriptGenerationService;
import com.pydlove.regent.deployment.application.command.GenerateDeploymentScriptCommand;
import com.pydlove.regent.deployment.application.query.GeneratedDeploymentScript;
import com.pydlove.regent.deployment.application.query.DeploymentServerOverview;
import com.pydlove.regent.deployment.application.query.DeploymentServerQueryService;
import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.deployment.domain.model.DeploymentScriptIssue;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeploymentControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DeploymentController(
                        new RecordingCommandService(),
                        null,
                        new RecordingScriptGenerationService(),
                        null,
                        null,
                        new StubServerQuery(),
                        new StubProjectAccess()))
                .addInterceptors(TestCurrentUser.interceptor())
                .build();
    }

    @Test
    @DisplayName("脚本检测返回逐行问题")
    void validatesScriptWithLineIssues() throws Exception {
        mockMvc.perform(post("/api/deployments/script-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scriptContent\":\"cd /opt/app\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues[0].line").value(3))
                .andExpect(jsonPath("$.issues[0].code").value("HARD_CODED_DIRECTORY"))
                .andExpect(jsonPath("$.issues[0].message").value("不要写死远程目录"));
    }

    @Test
    @DisplayName("AI 生成接口返回可直接填入表单的脚本")
    void generatesScriptForDeploymentForm() throws Exception {
        mockMvc.perform(post("/api/deployments/script-generation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "generate-test-123",
                                  "projectId": 7,
                                  "serverId": 9,
                                  "workspacePath": "/tmp/regent/service",
                                  "remoteDirectory": "/opt/regent"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scriptFilename").value("deploy_regent.sh"))
                .andExpect(jsonPath("$.scriptContent").value(
                        "#!/usr/bin/env bash\nset -Eeuo pipefail\necho deploy\n"));
    }

    @Test
    @DisplayName("停止接口把请求 ID 转交给生成服务")
    void cancelsScriptGeneration() throws Exception {
        RecordingScriptGenerationService service = new RecordingScriptGenerationService();
        mockMvc = MockMvcBuilders.standaloneSetup(new DeploymentController(
                        new RecordingCommandService(),
                        null,
                        service,
                        null,
                        null,
                        new StubServerQuery(),
                        new StubProjectAccess()))
                .addInterceptors(TestCurrentUser.interceptor())
                .build();

        mockMvc.perform(post("/api/deployments/script-generation/generate-test-123/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));

        assertEquals("generate-test-123", service.cancelledRequestId);
    }

    private static final class RecordingCommandService extends DeploymentCommandService {

        private RecordingCommandService() {
            super(null, null, null, null, null);
        }

        @Override
        public List<DeploymentScriptIssue> validateScript(String scriptContent) {
            return List.of(new DeploymentScriptIssue(
                    3, "HARD_CODED_DIRECTORY", "不要写死远程目录"));
        }
    }

    private static final class RecordingScriptGenerationService
            extends DeploymentScriptGenerationService {

        private String cancelledRequestId;

        private RecordingScriptGenerationService() {
            super(null, null, null);
        }

        @Override
        public GeneratedDeploymentScript generate(GenerateDeploymentScriptCommand command,
                                                  String requestId) {
            return new GeneratedDeploymentScript(
                    "deploy_regent.sh",
                    "#!/usr/bin/env bash\nset -Eeuo pipefail\necho deploy\n");
        }

        @Override
        public boolean cancel(String requestId) {
            cancelledRequestId = requestId;
            return true;
        }
    }

    private static final class StubProjectAccess extends ProjectAccessService {

        private StubProjectAccess() {
            super(null);
        }

        @Override
        public ProjectProfile require(ProjectId projectId, UserId ownerId) {
            return null;
        }
    }

    private static final class StubServerQuery extends DeploymentServerQueryService {

        private StubServerQuery() {
            super(null, null, null);
        }

        @Override
        public DeploymentServerOverview get(DeploymentServerId id, UserId ownerId) {
            return null;
        }
    }
}
