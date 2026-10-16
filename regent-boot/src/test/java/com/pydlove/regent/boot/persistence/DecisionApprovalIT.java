package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.governance.application.command.DecisionApprovalService;
import com.pydlove.regent.governance.application.command.ReviewCommand;
import com.pydlove.regent.governance.domain.model.ApprovalToken;
import com.pydlove.regent.governance.domain.model.Decision;
import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.governance.domain.model.DecisionRaise;
import com.pydlove.regent.governance.domain.model.DecisionStatus;
import com.pydlove.regent.governance.domain.model.ResolvedBy;
import com.pydlove.regent.governance.domain.repository.DecisionQueryRepository;
import com.pydlove.regent.governance.domain.repository.DecisionRepository;
import com.pydlove.regent.governance.domain.repository.DecisionView;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审批链路的集成测试 —— 覆盖「两个聚合 + 一枚令牌 + 一个事务」这条最长的写路径。
 *
 * <p>它必须用真库的理由有三条，都是假仓储验不出来的：{@code options} 是 JSON 列（
 * 领域层是 {@code List<String>}，中间隔着一次序列化）、{@code resolved_at} 是 DATETIME(3)
 * （时间往返精度）、以及整条路径跑在事务里（跨聚合写入的原子性）。
 *
 * <p>命名为 IT 结尾：需要真实 MySQL（schema regent_test），surefire 默认不认这个后缀，
 * 所以没有数据库的机器上 {@code mvn verify} 依然全绿。
 * 手动跑法：{@code mvn -pl regent-boot -am test -Dtest=DecisionApprovalIT}
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DecisionApprovalIT {

    private static final long PROBE_PROJECT_ID = 9999L;
    private static final ProjectId PROJECT = ProjectId.of(PROBE_PROJECT_ID);

    @Autowired
    private DecisionApprovalService approvalService;

    @Autowired
    private DecisionRepository decisionRepository;

    @Autowired
    private DecisionQueryRepository decisionQueryRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 准备探针项目() {
        jdbcTemplate.update("""
                INSERT INTO project (id, code, name, path, default_decision_level, enabled, deleted, created_at, updated_at)
                VALUES (?, 'it_probe', 'IT 探针项目', '/tmp/it', 'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE name = VALUES(name)
                """, PROBE_PROJECT_ID);
    }

    /** 造一条「阻塞在决策上」的任务 + 一条待批决策，返回决策。 */
    private Decision 待批决策(Task task) {
        Decision decision = Decision.raise(new DecisionRaise(
                task.getId(), PROJECT, DecisionLevel.L1, DecisionLevel.L2, null,
                "IT 探针决策：要不要给订单表加字段？",
                List.of("加字段并补索引", "先加字段"),
                "建议加字段并补索引", true, "仅探针数据"));
        decisionRepository.save(decision);
        task.block(decision.getId());
        taskRepository.save(task);
        return decision;
    }

    private Task 阻塞中的任务() {
        Task task = Task.manual(PROJECT, "IT 审批任务", "验证审批链路", 0);
        taskRepository.save(task);
        task.dispatch("codex/it-approval");
        taskRepository.save(task);
        return task;
    }

    @Test
    @DisplayName("批准后决策落已批准、任务回到执行中，选项列表往返一致")
    void 批准() {
        Task task = 阻塞中的任务();
        Decision decision = 待批决策(task);

        approvalService.approve(decision.getId().value(),
                ReviewCommand.of("加字段并补索引", "看过表结构，可以执行"));

        Decision loaded = decisionRepository.findById(decision.getId()).orElseThrow();
        assertEquals(DecisionStatus.APPROVED, loaded.getStatus());
        assertEquals(ResolvedBy.HUMAN, loaded.getResolvedBy().orElseThrow());
        assertEquals("加字段并补索引", loaded.getResolvedChoice().orElseThrow());
        assertEquals(List.of("加字段并补索引", "先加字段"), loaded.getOptions(),
                "options 是 JSON 列，往返后必须还是那两个选项");
        assertNotNull(loaded.getResolvedAt());

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.RUNNING, reloaded.getStatus());
        assertTrue(reloaded.getBlockingDecisionId().isEmpty());

        // 读模型走的是另一条 SQL（连表 + 命中规则名），单独断言一次，确认两条路径看到同一个结论
        DecisionView view = decisionQueryRepository.findById(decision.getId().value()).orElseThrow();
        assertEquals("APPROVED", view.status());
        assertEquals("IT 审批任务", view.taskTitle());
    }

    @Test
    @DisplayName("驳回后任务终止，理由留在决策单上")
    void 驳回() {
        Task task = 阻塞中的任务();
        Decision decision = 待批决策(task);

        approvalService.reject(decision.getId().value(),
                ReviewCommand.of(null, "这个改动会影响线上数据，必须人工确认后再执行"));

        Decision loaded = decisionRepository.findById(decision.getId()).orElseThrow();
        assertEquals(DecisionStatus.REJECTED, loaded.getStatus());
        assertTrue(loaded.getResolutionNote().orElseThrow().contains("线上数据"));

        assertEquals(TaskStatus.CANCELLED, taskRepository.findById(task.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("令牌核销后落 USED 并记来源 IP，同一枚不能再批第二次")
    void 令牌核销() {
        Task task = 阻塞中的任务();
        Decision decision = 待批决策(task);
        String rawToken = "it-raw-token";
        jdbcTemplate.update("""
                INSERT INTO approval_token (decision_id, token_hash, status, expires_at, created_at)
                VALUES (?, ?, 'ACTIVE', DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 7 DAY), UTC_TIMESTAMP(3))
                """, decision.getId().value(), ApprovalToken.hashOf(rawToken));

        approvalService.approve(decision.getId().value(),
                new ReviewCommand(null, null, rawToken, "10.0.0.9"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, used_ip FROM approval_token WHERE token_hash = ?",
                ApprovalToken.hashOf(rawToken));
        assertEquals("USED", row.get("status"));
        assertEquals("10.0.0.9", row.get("used_ip"));

        // 第二次：决策已决议，先被聚合守卫挡住
        assertThrows(DomainException.class, () -> approvalService.approve(decision.getId().value(),
                new ReviewCommand(null, null, rawToken, "10.0.0.9")));
    }

    @Test
    @DisplayName("令牌过期时拒绝审批，决策保持待批")
    void 令牌过期() {
        Task task = 阻塞中的任务();
        Decision decision = 待批决策(task);
        String rawToken = "it-expired-token";
        jdbcTemplate.update("""
                INSERT INTO approval_token (decision_id, token_hash, status, expires_at, created_at)
                VALUES (?, ?, 'ACTIVE', DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 MINUTE), UTC_TIMESTAMP(3))
                """, decision.getId().value(), ApprovalToken.hashOf(rawToken));

        assertThrows(DomainException.class, () -> approvalService.approve(decision.getId().value(),
                new ReviewCommand(null, null, rawToken, "10.0.0.9")));

        assertEquals(DecisionStatus.PENDING,
                decisionRepository.findById(decision.getId()).orElseThrow().getStatus());
        assertEquals(TaskStatus.BLOCKED, taskRepository.findById(task.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("任务已被人工终止时，驳回只落决策不回滚")
    void 任务已终止时驳回() {
        Task task = 阻塞中的任务();
        Decision decision = 待批决策(task);
        Task cancelled = taskRepository.findById(task.getId()).orElseThrow();
        cancelled.cancel();
        taskRepository.save(cancelled);

        approvalService.reject(decision.getId().value(), ReviewCommand.of(null, "任务已经不要了"));

        assertEquals(DecisionStatus.REJECTED,
                decisionRepository.findById(decision.getId()).orElseThrow().getStatus());
        assertEquals(TaskStatus.CANCELLED, taskRepository.findById(task.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("决策不存在时报未找到")
    void 决策不存在() {
        assertThrows(EntityNotFoundException.class,
                () -> approvalService.approve(999_999L, ReviewCommand.of(null, null)));
    }

    @Test
    @DisplayName("新决策落库后待批，且自评与生效等级都保留")
    void 建单落库() {
        Task task = 阻塞中的任务();
        Decision decision = 待批决策(task);

        Decision loaded = decisionRepository.findById(decision.getId()).orElseThrow();

        assertNotNull(decision.getId(), "insert 后必须回填数据库生成的主键");
        assertEquals(DecisionStatus.PENDING, loaded.getStatus());
        assertEquals(DecisionLevel.L1, loaded.getAiLevel());
        assertEquals(DecisionLevel.L2, loaded.getEffectiveLevel());
        assertNull(loaded.getResolvedAt().orElse(null));
        assertTrue(loaded.isReversible());
    }
}
