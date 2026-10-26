package com.pydlove.regent.deployment.interfaces.rest;

import com.pydlove.regent.deployment.application.command.DeploymentCommandService;
import com.pydlove.regent.deployment.application.command.DeploymentExecutionService;
import com.pydlove.regent.deployment.application.command.DeploymentScriptGenerationService;
import com.pydlove.regent.deployment.application.command.GenerateDeploymentScriptCommand;
import com.pydlove.regent.deployment.application.query.GeneratedDeploymentScript;
import com.pydlove.regent.deployment.application.query.DeploymentServerOverview;
import com.pydlove.regent.deployment.application.query.DeploymentServerQueryService;
import com.pydlove.regent.deployment.application.query.DeploymentQueryService;
import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;
import com.pydlove.regent.deployment.domain.model.DeploymentRunId;
import com.pydlove.regent.deployment.domain.model.DeploymentRunStatus;
import com.pydlove.regent.deployment.domain.model.DeploymentReviewReference;
import com.pydlove.regent.deployment.domain.model.DeploymentServer;
import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.deployment.domain.model.DeploymentScriptIssue;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.query.ProjectAccessService;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.review.application.command.ReviewGateApproval;
import com.pydlove.regent.review.application.command.ReviewGateService;
import com.pydlove.regent.support.TestCurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeploymentControllerTest {

    private MockMvc mockMvc;
    private RecordingExecutionService executionService;

    @BeforeEach
    void setUp() {
        executionService = new RecordingExecutionService();
        mockMvc = MockMvcBuilders.standaloneSetup(new DeploymentController(
                        new RecordingCommandService(),
                        executionService,
                        new RecordingScriptGenerationService(),
                        new StubDeploymentQuery(),
                        null,
                        new StubServerQuery(),
                        new StubProjectAccess(),
                        new StubReviewGate()))
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
                        new StubProjectAccess(),
                        new StubReviewGate()))
                .addInterceptors(TestCurrentUser.interceptor())
                .build();

        mockMvc.perform(post("/api/deployments/script-generation/generate-test-123/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));

        assertEquals("generate-test-123", service.cancelledRequestId);
    }

    @Test
    @DisplayName("停止部署接口返回已中断的执行记录")
    void stopsActiveDeployment() throws Exception {
        mockMvc.perform(post("/api/deployments/1/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.status").value("INTERRUPTED"));

        assertEquals(DeploymentId.of(1L), executionService.stoppedDeploymentId);
    }

    @Test
    @DisplayName("执行部署时可以把目标项目传给调度服务")
    void runsDeploymentInSelectedProject() throws Exception {
        mockMvc.perform(post("/api/deployments/1/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(8));

        assertEquals(DeploymentId.of(1L), executionService.startedDeploymentId);
        assertEquals(ProjectId.of(8L), executionService.startedProjectId);
        assertEquals(null, executionService.startedReviewReference);
    }

    @Test
    @DisplayName("关联任务部署先经过审阅门禁并记录审计引用")
    void gatesTaskDeployment() throws Exception {
        mockMvc.perform(post("/api/deployments/1/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":8,\"taskId\":11}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(11))
                .andExpect(jsonPath("$.reviewSnapshotId").value(41))
                .andExpect(jsonPath("$.reviewDecisionId").value(42));

        assertEquals(11L, executionService.startedReviewReference.taskId());
        assertEquals(41L, executionService.startedReviewReference.snapshotId());
        assertEquals(42L, executionService.startedReviewReference.decisionId());
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

    private static final class RecordingExecutionService extends DeploymentExecutionService {

        private DeploymentId stoppedDeploymentId;
        private DeploymentId startedDeploymentId;
        private ProjectId startedProjectId;
        private DeploymentReviewReference startedReviewReference;

        private RecordingExecutionService() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public DeploymentRun stopActive(DeploymentId deploymentId) {
            stoppedDeploymentId = deploymentId;
            Deployment deployment = Deployment.create(
                    ProjectId.of(7L), DeploymentServerId.of(9L), "生产",
                    "/opt/app", "deploy.sh", "echo ok");
            deployment.assignIdentity(DeploymentId.of(1L));
            DeploymentServer server = DeploymentServer.create(
                    ProjectId.of(7L), "生产服务器", "api.example.com", 22,
                    "root", DeploymentAuthType.KEY);
            server.assignIdentity(DeploymentServerId.of(9L));
            DeploymentRun run = DeploymentRun.queue(deployment.snapshot(), server.snapshot());
            run.assignIdentity(DeploymentRunId.of(12L));
            run.interrupt(DeploymentExecutionService.USER_INTERRUPT_REASON);
            return run;
        }

        @Override
        public DeploymentRun start(DeploymentId deploymentId,
                                   ProjectId projectId,
                                   DeploymentReviewReference reviewReference) {
            startedDeploymentId = deploymentId;
            startedProjectId = projectId;
            startedReviewReference = reviewReference;
            Deployment deployment = Deployment.create(
                    List.of(ProjectId.of(7L), ProjectId.of(8L)),
                    DeploymentServerId.of(9L), "多项目部署",
                    "/opt/app", "deploy.sh", "#!/usr/bin/env bash\nset -Eeuo pipefail\necho ok\n");
            deployment.assignIdentity(DeploymentId.of(1L));
            DeploymentServer server = DeploymentServer.create(
                    List.of(ProjectId.of(7L), ProjectId.of(8L)),
                    "生产服务器", "api.example.com", 22,
                    "root", DeploymentAuthType.KEY);
            server.assignIdentity(DeploymentServerId.of(9L));
            DeploymentRun run = DeploymentRun.queue(
                    deployment.snapshot(projectId), server.snapshot(), reviewReference);
            run.assignIdentity(DeploymentRunId.of(13L));
            return run;
        }
    }

    private static final class StubDeploymentQuery extends DeploymentQueryService {

        private StubDeploymentQuery() {
            super(null, null, null, null, null);
        }

        @Override
        public com.pydlove.regent.deployment.application.query.DeploymentOverview get(
                DeploymentId id,
                UserId ownerId) {
            return null;
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

    private static final class StubReviewGate extends ReviewGateService {

        private StubReviewGate() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public ReviewGateApproval requireApproved(TaskId taskId, UserId ownerId) {
            return new ReviewGateApproval(taskId.value(), 8L, 41L, 42L);
        }
    }
}
