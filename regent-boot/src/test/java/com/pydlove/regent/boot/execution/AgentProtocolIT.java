package com.pydlove.regent.boot.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.execution.application.command.AgentRunService;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.service.ExecutionConcurrencyGate;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.governance.application.command.DecisionApprovalService;
import com.pydlove.regent.governance.application.command.ReviewCommand;
import com.pydlove.regent.plugin.application.command.PluginCommandService;
import com.pydlove.regent.plugin.application.command.SavePluginCommand;
import com.pydlove.regent.plugin.application.command.TaskPluginService;
import com.pydlove.regent.plugin.domain.model.Plugin;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 外部执行者协议的端到端集成测试 —— 真实 MySQL、真实 Git 仓库、真实 HTTP 层。
 *
 * <p>它盯的是「别的工具照着自己写代码」这件事最容易出错的几处：字段名、状态码、
 * SQL 里那几条按项目与租约的查询，以及一条任务的完整往返（清单 → 认领 → 上报 →
 * 自动放行或挂起 → 批准后续跑）。这些都是单测的替身覆盖不到的地方：
 * 替身永远同意你对 SQL 的想象。
 *
 * <p>为什么走 MockMvc 而不是直接调应用服务：协议就是那串 HTTP 字段名，
 * 跳过 HTTP 层等于把最容易改名分叉的一层留空。
 *
 * <p>刻意以 IT 结尾：没有 MySQL 的机器上 {@code mvn verify} 依然全绿。
 * 手动跑法：{@code mvn -pl regent-boot -am test -Dtest=AgentProtocolIT}
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgentProtocolIT {

    private static final long PROJECT_ID = 9802L;
    private static final String PROJECT_CODE = "it_agent";
    private static final String AGENT = "codex-desktop@it";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskRunRepository taskRunRepository;

    @Autowired
    private AgentRunService agentRunService;

    @Autowired
    private DecisionApprovalService decisionApprovalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PluginCommandService pluginCommandService;

    @Autowired
    private TaskPluginService taskPluginService;

    @Autowired
    private ExecutionConcurrencyGate concurrencyGate;

    private Path workspace;

    @BeforeEach
    void 准备临时仓库() throws IOException {
        concurrencyGate.updateLimit(64);
        workspace = Files.createTempDirectory("regent-it-agent-");
        git("init", "--quiet");
        git("config", "user.email", "it@regent.local");
        git("config", "user.name", "Regent IT");
        Files.writeString(workspace.resolve("README.md"), "初始内容\n", StandardCharsets.UTF_8);
        git("add", "README.md");
        git("commit", "--quiet", "-m", "初始提交");

        jdbcTemplate.update("""
                INSERT INTO app_user (id, username, display_name, password_hash, email, created_at)
                VALUES (1, 'agent_protocol_it', 'Agent 协议测试', 'x',
                        'agent-protocol-it@example.com', UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE display_name = VALUES(display_name)
                """);
        jdbcTemplate.update("""
                INSERT INTO project (id, owner_id, code, name, path, default_decision_level,
                                     enabled, deleted, created_at, updated_at)
                VALUES (?, 1, ?, 'IT 协议项目', ?, 'L1',
                        1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE owner_id = VALUES(owner_id), path = VALUES(path),
                                        default_decision_level = 'L1', enabled = 1, deleted = 0
                """, PROJECT_ID, PROJECT_CODE, workspace.toString());
    }

    @AfterEach
    void 清理临时产物() throws IOException {
        deleteRecursively(workspace);
    }

    @Test
    @DisplayName("一份契约读走就够：协议版本、端点表、出参 schema、协议全文都在")
    void 契约自描述() throws Exception {
        mockMvc.perform(get("/api/agent/contract"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protocol").value("regent-agent/1"))
                .andExpect(jsonPath("$.endpoints.length()").value(6))
                .andExpect(jsonPath("$.levels.length()").value(4))
                .andExpect(jsonPath("$.resultSchema.properties.status.enum[0]").value("DONE"))
                .andExpect(jsonPath("$.contract").value(org.hamcrest.Matchers.containsString("外部执行者")));

        String markdown = mockMvc.perform(get("/api/agent/contract.md"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(markdown.contains("regent-agent/1"), "纯文本版要能直接贴进任何工具的上下文");
        assertTrue(markdown.contains("平台上报约定"), "协议全文要与内部执行器读到的那份拼在一起");
    }

    @Test
    @DisplayName("按项目走一遍全流程：清单 → 认领 → 阈值内自动放行 → 报完成")
    void 全流程() throws Exception {
        Task task = 新建任务("把 README 改一句", "把 README 的措辞改得更准确");

        mockMvc.perform(get("/api/agent/tasks").param("project", PROJECT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskId").value(task.getId().value()))
                .andExpect(jsonPath("$[0].projectCode").value(PROJECT_CODE))
                .andExpect(jsonPath("$[0].claimable").value(true));

        JsonNode claimed = 认领(null);
        long runId = claimed.get("runId").asLong();
        assertTrue(claimed.get("instruction").asText().contains("把 README 的措辞改得更准确"),
                "工作包里要带原始需求");
        assertTrue(claimed.get("instruction").asText().contains("上报约定"), "指令末尾要带上报约定");
        assertEquals(TaskStatus.RUNNING, reload(task.getId()).getStatus());

        // 阈值 L1 内的决策：平台当场放行，同一次尝试接着干
        mockMvc.perform(post("/api/agent/runs/{id}/report", runId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"executor":"%s","sessionId":"sess-1","status":"BLOCKED","summary":"想改个措辞",
                                 "changedFiles":["README.md"],
                                 "decision":{"level":"L0","question":"改还是不改？","options":["改"],
                                 "recommendation":"改","reversible":true,"blastRadius":"文档"},"bugs":[]}""".formatted(AGENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CONTINUE"))
                .andExpect(jsonPath("$.instruction").value(org.hamcrest.Matchers.containsString("决策已归位")));

        mockMvc.perform(post("/api/agent/runs/{id}/report", runId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"executor":"%s","sessionId":"sess-1","status":"DONE","summary":"改完了",
                                 "changedFiles":["README.md"],"decision":null,"bugs":[],
                                 "tokens":{"input":900,"cachedInput":0,"output":120}}""".formatted(AGENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DONE"));

        Task finished = reload(task.getId());
        assertEquals(TaskStatus.DONE, finished.getStatus());
        TaskRun run = taskRunRepository.findById(TaskRunId.of(runId)).orElseThrow();
        assertEquals(RunStatus.SUCCESS, run.getStatus());
        assertEquals(900L, run.getTokens().input(), "执行者自报的 token 要落库");
        assertEquals(AGENT, run.getAgentName());
    }

    @Test
    @DisplayName("工作包携带任务绑定的固定版本插件")
    void 工作包携带插件() throws Exception {
        Task task = 新建任务("读取 Kafka", "复用 Kafka 组件读取消息");
        Plugin plugin = pluginCommandService.create(new SavePluginCommand(
                "Kafka 读取组件",
                "已验证的消费者",
                List.of("Kafka"),
                "KafkaReader.java",
                "class KafkaReader { void read() {} }",
                true));
        taskPluginService.bind(task.getId(), List.of(plugin.getId()));

        JsonNode claimed = 认领(task.getId().value());

        assertEquals(1, claimed.get("plugins").size());
        assertEquals(1, claimed.get("plugins").get(0).get("revision").asInt());
        assertTrue(claimed.get("plugins").get(0).get("content").asText().contains("KafkaReader"));
        assertTrue(claimed.get("instruction").asText().contains("不要重新生成已有实现"));
    }

    @Test
    @DisplayName("超阈值：挂起等人 → 批准 → 心跳叫人重新认领 → 指令里带着人的结论")
    void 挂起与人批准() throws Exception {
        Task task = 新建任务("动一下依赖", "把构建插件升级到最新");
        long runId = 认领(null).get("runId").asLong();

        String blocked = mockMvc.perform(post("/api/agent/runs/{id}/report", runId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"executor":"%s","sessionId":"sess-1","status":"BLOCKED","summary":"要动依赖",
                                 "changedFiles":["pom.xml"],
                                 "decision":{"level":"L2","question":"要不要升级构建插件？","options":["升级","先不动"],
                                 "recommendation":"先不动","reversible":true,"blastRadius":"构建"},"bugs":[]}""".formatted(AGENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("BLOCKED"))
                .andExpect(jsonPath("$.decisionLevel").value("L2"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long decisionId = objectMapper.readTree(blocked).get("decisionId").asLong();
        assertEquals(TaskStatus.BLOCKED, reload(task.getId()).getStatus());

        mockMvc.perform(post("/api/agent/runs/{id}/heartbeat", runId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executor\":\"" + AGENT + "\"}"))
                .andExpect(jsonPath("$.nextAction").value("WAIT_DECISION"));

        decisionApprovalService.approve(decisionId, ReviewCommand.of("先不动", "等下个版本一起升"));

        mockMvc.perform(post("/api/agent/runs/{id}/heartbeat", runId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executor\":\"" + AGENT + "\"}"))
                .andExpect(jsonPath("$.nextAction").value("CLAIM_AGAIN"));

        JsonNode resumed = 认领(task.getId().value());
        assertEquals(2, resumed.get("attemptNo").asInt());
        assertTrue(resumed.get("instruction").asText().contains("人工裁决为：先不动"),
                resumed.get("instruction").asText());
    }

    @Test
    @DisplayName("执行者中途消失：租约到期判失联，任务回到可以重试的状态")
    void 租约到期判失联() throws Exception {
        Task task = 新建任务("跑一半就没了", "做到一半执行者被关掉了");
        long runId = 认领(null).get("runId").asLong();

        // 把租约推回一小时前，等价于执行者已经很久没有心跳
        jdbcTemplate.update("UPDATE task_run SET lease_expires_at = UTC_TIMESTAMP(3) - INTERVAL 1 HOUR WHERE id = ?", runId);

        assertEquals(1, agentRunService.releaseExpiredLeases());

        TaskRun run = taskRunRepository.findById(TaskRunId.of(runId)).orElseThrow();
        assertEquals(RunStatus.FAILED, run.getStatus());
        assertTrue(run.getSummary().contains("失联"), run.getSummary());
        assertEquals(TaskStatus.FAILED, reload(task.getId()).getStatus());
    }

    @Test
    @DisplayName("没活时是 204：对轮询的执行者来说，「现在没活」是一个正常答案")
    void 没活返回204() throws Exception {
        mockMvc.perform(post("/api/agent/tasks/claim").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"executor":"%s","sessionId":"sess-1","projectCode":"%s"}"""
                                .formatted(AGENT, PROJECT_CODE)))
                .andExpect(status().isNoContent());
    }

    private JsonNode 认领(Long taskId) throws Exception {
        String body = mockMvc.perform(post("/api/agent/tasks/claim").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"executor":"%s","sessionId":"sess-1","projectCode":"%s","taskId":%s}"""
                                .formatted(AGENT, PROJECT_CODE, taskId == null ? "null" : taskId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private Task 新建任务(String title, String prompt) {
        Task task = Task.manual(ProjectId.of(PROJECT_ID), title, prompt, 0);
        taskRepository.save(task);
        return task;
    }

    private Task reload(TaskId id) {
        return taskRepository.findById(id).orElseThrow();
    }

    private String git(String... args) throws IOException {
        List<String> command = new ArrayList<>(List.of("git", "-C", workspace.toString()));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("git " + String.join(" ", args) + " 失败：" + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("git 被中断", e);
        }
        return output;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时目录清理失败不影响断言
                }
            });
        }
    }
}
