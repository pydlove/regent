package com.pydlove.regent.governance.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.governance.domain.model.ApprovalToken;
import com.pydlove.regent.governance.domain.model.Decision;
import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.governance.domain.model.DecisionRaise;
import com.pydlove.regent.governance.domain.model.DecisionStatus;
import com.pydlove.regent.governance.domain.repository.ApprovalTokenRepository;
import com.pydlove.regent.governance.domain.repository.DecisionRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审批用例测试 —— 跨聚合编排的那一段。
 *
 * <p>它用假仓储跑，不起 Spring 也不连数据库：要验证的是「先守卫、后落库」这个顺序，
 * 而顺序错误造成的后果（半个状态落库）恰好在内存里最容易断言 ——
 * 看写了几个对象就行。
 */
class DecisionApprovalServiceTest {

    private static final long TASK_ID = 42L;
    private static final DecisionId DECISION_ID = DecisionId.of(7L);

    private FakeDecisionRepository decisions;
    private FakeApprovalTokenRepository tokens;
    private FakeTaskRepository tasks;
    private CountingTransactionRunner transactions;
    private DecisionApprovalService service;

    @BeforeEach
    void setUp() {
        decisions = new FakeDecisionRepository();
        tokens = new FakeApprovalTokenRepository();
        tasks = new FakeTaskRepository();
        transactions = new CountingTransactionRunner();
        service = new DecisionApprovalService(decisions, tokens, tasks, transactions);
    }

    private Decision pendingDecision() {
        Decision decision = Decision.raise(new DecisionRaise(
                TaskId.of(TASK_ID), ProjectId.of(1L), DecisionLevel.L1, DecisionLevel.L2, 3L,
                "要不要给订单表加 refund_no 字段？", List.of("加字段并补索引", "先加字段"),
                "建议加字段并补索引", true, "orders 表"));
        decision.assignIdentity(DECISION_ID);
        decisions.save(decision);
        return decision;
    }

    private Task blockedTask() {
        Task task = Task.manual(ProjectId.of(1L), "加字段", "给订单表加 refund_no", 5);
        task.assignIdentity(TaskId.of(TASK_ID));
        task.dispatch("codex/task-42");
        task.block(DECISION_ID);
        tasks.save(task);
        return task;
    }

    /**
     * 把计数清零。布置场景本身也会写库，不清零的话断言里夹着布置阶段的写入次数，
     * 一旦布置方式变了（比如多存一次决策），断言就会失败在一个与被测行为无关的地方。
     */
    private void resetCounters() {
        decisions.resetSaves();
        tasks.resetSaves();
        tokens.resetConsumes();
    }

    @Nested
    @DisplayName("批准")
    class Approve {

        @Test
        void 决策落已批准且任务从阻塞回到执行中() {
            Decision decision = pendingDecision();
            Task task = blockedTask();
            resetCounters();

            Decision result = service.approve(DECISION_ID.value(), ReviewCommand.of("加字段并补索引", "可以执行"));

            assertEquals(DecisionStatus.APPROVED, result.getStatus());
            assertEquals(TaskStatus.RUNNING, task.getStatus());
            assertTrue(task.getBlockingDecisionId().isEmpty(), "续跑后不该还挂着决策单号");
            assertEquals(1, tasks.saveCount());
            assertEquals(1, decisions.saveCount());
        }

        @Test
        void 任务不在阻塞中时拒绝且一行都不写() {
            pendingDecision();
            Task task = blockedTask();
            task.cancel();
            resetCounters();

            assertThrows(DomainException.class,
                    () -> service.approve(DECISION_ID.value(), ReviewCommand.of(null, null)));

            // 关键断言：守卫在落库之前跑，失败时库里不该有任何变化
            assertEquals(DecisionStatus.PENDING, decisions.findById(DECISION_ID).orElseThrow().getStatus());
            assertEquals(0, tasks.saveCount());
        }

        @Test
        void 任务阻塞在另一条决策上时拒绝() {
            pendingDecision();
            Task task = blockedTask();
            task.resume();
            task.block(DecisionId.of(999L));
            resetCounters();

            DomainException e = assertThrows(DomainException.class,
                    () -> service.approve(DECISION_ID.value(), ReviewCommand.of(null, null)));

            assertTrue(e.getMessage().contains("另一条决策"), e.getMessage());
            assertEquals(TaskStatus.BLOCKED, task.getStatus());
            assertEquals(0, tasks.saveCount());
        }
    }

    @Nested
    @DisplayName("驳回")
    class Reject {

        @Test
        void 决策落已驳回且阻塞中的任务终止() {
            Decision decision = pendingDecision();
            Task task = blockedTask();
            resetCounters();

            Decision result = service.reject(DECISION_ID.value(), ReviewCommand.of(null, "会影响线上数据"));

            assertEquals(DecisionStatus.REJECTED, result.getStatus());
            assertEquals(TaskStatus.CANCELLED, task.getStatus());
            assertNull(task.getBlockingDecisionId().orElse(null));
        }

        @Test
        void 任务已被人工终止时只留决策记录() {
            pendingDecision();
            Task task = blockedTask();
            task.cancel();
            resetCounters();

            Decision result = service.reject(DECISION_ID.value(), ReviewCommand.of(null, "重复驳回"));

            // 硬要给一个已终态的任务判终止只会抛异常，把一次正常的人工否决变成 500
            assertEquals(DecisionStatus.REJECTED, result.getStatus());
            assertEquals(0, tasks.saveCount(), "任务已经是终态，不该再写它");
            assertEquals(1, decisions.saveCount(), "但决策记录必须落下来");
        }

        @Test
        void 驳回理由为空时拒绝且不写库() {
            pendingDecision();
            blockedTask();
            resetCounters();

            assertThrows(DomainException.class,
                    () -> service.reject(DECISION_ID.value(), ReviewCommand.of(null, "  ")));

            assertEquals(DecisionStatus.PENDING, decisions.findById(DECISION_ID).orElseThrow().getStatus());
            assertEquals(0, tasks.saveCount());
        }
    }

    @Nested
    @DisplayName("令牌")
    class Token {

        @Test
        void 带正确令牌时通过并核销() {
            pendingDecision();
            blockedTask();
            tokens.add(DECISION_ID, "raw-token", Instant.now().plusSeconds(600));
            resetCounters();

            service.approve(DECISION_ID.value(), new ReviewCommand(null, null, "raw-token", "10.0.0.9"));

            assertEquals(1, tokens.consumeCount());
            assertEquals("10.0.0.9", tokens.consumedIp);
        }

        @Test
        void 控制台路径不带令牌也能通过() {
            pendingDecision();
            blockedTask();

            service.approve(DECISION_ID.value(), new ReviewCommand(null, null, null, "127.0.0.1"));

            assertEquals(0, tokens.consumeCount(), "没令牌就没有可核销的东西");
        }

        @Test
        void 令牌不存在时拒绝() {
            pendingDecision();
            blockedTask();
            resetCounters();

            assertThrows(DomainException.class, () -> service.approve(DECISION_ID.value(),
                    new ReviewCommand(null, null, "forged-token", "1.2.3.4")));

            assertEquals(DecisionStatus.PENDING, decisions.findById(DECISION_ID).orElseThrow().getStatus());
        }

        @Test
        void 令牌属于别的决策时拒绝() {
            pendingDecision();
            blockedTask();
            tokens.add(DecisionId.of(9L), "other-token", Instant.now().plusSeconds(600));

            DomainException e = assertThrows(DomainException.class, () -> service.approve(DECISION_ID.value(),
                    new ReviewCommand(null, null, "other-token", "1.2.3.4")));

            assertTrue(e.getMessage().contains("不匹配"), e.getMessage());
        }

        @Test
        void 令牌已过期时拒绝() {
            pendingDecision();
            blockedTask();
            tokens.add(DECISION_ID, "stale-token", Instant.now().minusSeconds(1));
            resetCounters();

            DomainException e = assertThrows(DomainException.class, () -> service.approve(DECISION_ID.value(),
                    new ReviewCommand(null, null, "stale-token", "1.2.3.4")));

            assertTrue(e.getMessage().contains("已使用或已过期"), e.getMessage());
            assertEquals(0, tokens.consumeCount());
        }
    }

    @Nested
    @DisplayName("不存在的对象")
    class Missing {

        @Test
        void 决策不存在时报未找到() {
            assertThrows(EntityNotFoundException.class,
                    () -> service.approve(404L, ReviewCommand.of(null, null)));
        }

        @Test
        void 决策所属任务不存在时报未找到() {
            pendingDecision();

            assertThrows(EntityNotFoundException.class,
                    () -> service.approve(DECISION_ID.value(), ReviewCommand.of(null, null)));
        }
    }

    @Test
    void 审批走事务边界() {
        pendingDecision();
        blockedTask();
        resetCounters();

        service.approve(DECISION_ID.value(), ReviewCommand.of(null, null));

        assertEquals(1, transactions.count, "整个用例必须在一个事务里，否则会留下半个状态");
    }

    // ---------------------------------------------------------------------
    // 测试替身。都是最小实现：只记「被调用了几次」，不做任何真实持久化。
    // ---------------------------------------------------------------------

    private static final class FakeDecisionRepository implements DecisionRepository {

        private final Map<Long, Decision> store = new HashMap<>();
        private int saves;

        @Override
        public Optional<Decision> findById(DecisionId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public void save(Decision decision) {
            store.put(decision.getId().value(), decision);
            saves++;
        }

        @Override
        public Optional<Decision> findLatestResolvedByTask(TaskId taskId) {
            return store.values().stream()
                    .filter(decision -> decision.getTaskId().equals(taskId))
                    .filter(decision -> !decision.isPending())
                    .max(Comparator.comparing(decision -> decision.getId().value()));
        }

        int saveCount() {
            return saves;
        }

        void resetSaves() {
            saves = 0;
        }
    }

    private static final class FakeApprovalTokenRepository implements ApprovalTokenRepository {

        private final List<ApprovalToken> tokens = new ArrayList<>();
        private int consumed;
        private String consumedIp;

        void add(DecisionId decisionId, String rawToken, Instant expiresAt) {
            tokens.add(new ApprovalToken(tokens.size() + 1L, decisionId, ApprovalToken.hashOf(rawToken),
                    ApprovalToken.STATUS_ACTIVE, expiresAt));
        }

        @Override
        public Optional<ApprovalToken> findByHash(String tokenHash) {
            return tokens.stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
        }

        @Override
        public void consume(long tokenId, String usedIp, Instant usedAt) {
            consumed++;
            consumedIp = usedIp;
        }

        int consumeCount() {
            return consumed;
        }

        void resetConsumes() {
            consumed = 0;
            consumedIp = null;
        }
    }

    private static final class FakeTaskRepository implements TaskRepository {

        private final Map<Long, Task> store = new HashMap<>();
        private int saves;

        @Override
        public Optional<Task> findById(TaskId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public void save(Task task) {
            store.put(task.getId().value(), task);
            saves++;
        }

        @Override
        public void updateEditable(Task task) {
            store.put(task.getId().value(), task);
            saves++;
        }

        @Override
        public void delete(TaskId id) {
            store.remove(id.value());
        }

        @Override
        public List<Task> findDispatchable(int limit) {
            return store.values().stream()
                    .filter(task -> task.getStatus() == TaskStatus.PENDING
                            || task.getStatus() == TaskStatus.QUEUED)
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<Task> findQueued(int limit) {
            return store.values().stream()
                    .filter(task -> task.getStatus() == TaskStatus.QUEUED)
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<Task> findDispatchableInProject(ProjectId projectId, int limit) {
            return findDispatchable(limit).stream()
                    .filter(task -> task.getProjectId().equals(projectId))
                    .toList();
        }

        int saveCount() {
            return saves;
        }

        void resetSaves() {
            saves = 0;
        }
    }

    /** 事务替身：直接执行，只数被调用了几次 —— 事务行为本身由集成测试验证。 */
    private static final class CountingTransactionRunner implements TransactionRunner {

        private int count;

        @Override
        public <T> T inTransaction(Supplier<T> action) {
            count++;
            return action.get();
        }
    }
}
