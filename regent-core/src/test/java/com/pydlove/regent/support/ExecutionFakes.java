package com.pydlove.regent.support;

import com.pydlove.regent.bug.domain.model.Bug;
import com.pydlove.regent.bug.domain.model.BugAttachment;
import com.pydlove.regent.bug.domain.model.BugAttachmentId;
import com.pydlove.regent.bug.domain.model.BugId;
import com.pydlove.regent.bug.domain.repository.BugAttachmentRepository;
import com.pydlove.regent.bug.domain.repository.BugRepository;
import com.pydlove.regent.execution.domain.model.AttachmentId;
import com.pydlove.regent.execution.domain.model.ExecDecision;
import com.pydlove.regent.execution.domain.model.ExecRequest;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.RunEvent;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskAttachment;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.port.AgentExecutor;
import com.pydlove.regent.execution.domain.port.BranchManager;
import com.pydlove.regent.execution.domain.port.BranchPlan;
import com.pydlove.regent.execution.domain.port.RunEventListener;
import com.pydlove.regent.execution.domain.repository.RunEventRepository;
import com.pydlove.regent.execution.domain.repository.TaskAttachmentRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.governance.domain.model.Decision;
import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.governance.domain.model.DecisionRule;
import com.pydlove.regent.governance.domain.repository.DecisionRepository;
import com.pydlove.regent.governance.domain.repository.DecisionRuleRepository;
import com.pydlove.regent.plugin.domain.model.PluginBinding;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.plugin.domain.repository.TaskPluginRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.EnvironmentEntry;
import com.pydlove.regent.project.domain.model.EnvironmentId;
import com.pydlove.regent.project.domain.model.EnvironmentScope;
import com.pydlove.regent.project.domain.repository.EnvironmentEntryRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.shared.domain.port.AttachmentStorage;
import com.pydlove.regent.shared.kernel.AttachmentScope;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.shared.kernel.TokenUsage;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 执行链路的内存替身。
 *
 * <p>集中在一个文件里而不是散成八个：这些替身只服务于 {@code execution} 与 {@code governance}
 * 两三个测试类，散开放会把测试目录变成一片需要来回跳转的森林。每个替身都只记「被调用了几次、
 * 收到了什么」，不做任何真实持久化 —— 单测要的是毫秒级反馈，不是微型数据库。
 */
public final class ExecutionFakes {

    private ExecutionFakes() {
    }

    /** 脚本化的执行器：按顺序回放准备好的结果，并记下每次请求与事件。 */
    public static final class Executor implements AgentExecutor {

        private final Deque<ExecResult> scripted = new ArrayDeque<>();
        public final List<ExecRequest> requests = new ArrayList<>();
        public final List<String> eventTypes = new ArrayList<>();
        public String reportedThreadId = "th-fake-1";
        /** 执行器被拉起来时先做一件事 —— 用来模拟「人在这时候点了终止」这类并发改动。 */
        public java.util.function.Consumer<ExecRequest> onExecute;

        public Executor script(ExecResult... results) {
            scripted.addAll(List.of(results));
            return this;
        }

        @Override
        public ExecResult execute(ExecRequest request, RunEventListener listener) {
            requests.add(request);
            if (onExecute != null) {
                onExecute.accept(request);
            }
            listener.onEvent("thread.started", "{\"type\":\"thread.started\"}");
            eventTypes.add("thread.started");
            ExecResult next = scripted.pollFirst();
            if (next == null) {
                throw new IllegalStateException("脚本已经放完，但执行器又被调了一次");
            }
            return next;
        }

        public static ExecResult success(String summary) {
            return new ExecResult(RunStatus.SUCCESS, 0, "th-fake-1", TokenUsage.of(100, 20, 30),
                    summary, summary, true, List.of("README.md"), List.of(), null);
        }

        public static ExecResult failed(String summary) {
            return new ExecResult(RunStatus.FAILED, 1, "th-fake-1", TokenUsage.of(10, 0, 5),
                    summary, summary, true, List.of(), List.of(), null);
        }

        public static ExecResult blocked(DecisionLevel level, boolean reversible) {
            return new ExecResult(RunStatus.BLOCKED, 0, "th-fake-1", TokenUsage.of(50, 0, 10),
                    "停下来问一句", null, true, List.of("pom.xml"), List.of(),
                    new ExecDecision(level, "要不要动依赖？", List.of("动", "不动"), "不动", reversible, "构建"));
        }
    }

    /** 不真的切分支，只记下被要求切到哪。 */
    public static final class Branches implements BranchManager {

        public final List<String> created = new ArrayList<>();
        public final List<String> switched = new ArrayList<>();
        public String current = "main";
        /** 默认模拟「分支已获准/已存在」；审批用例显式设为 true。 */
        public boolean requiresCreation;
        public RuntimeException failure;

        @Override
        public BranchPlan plan(String projectPath, TaskId taskId) {
            if (failure != null) {
                throw failure;
            }
            return new BranchPlan("codex/task-" + taskId.value(), requiresCreation);
        }

        @Override
        public String createFor(String projectPath, TaskId taskId) {
            if (failure != null) {
                throw failure;
            }
            String branch = "codex/task-" + taskId.value();
            created.add(branch);
            current = branch;
            return branch;
        }

        @Override
        public void switchTo(String projectPath, String branchName) {
            if (failure != null) {
                throw failure;
            }
            switched.add(branchName);
            current = branchName;
        }
    }

    public static final class Environments implements EnvironmentEntryRepository {

        private final Map<Long, EnvironmentEntry> store = new LinkedHashMap<>();
        private long sequence;

        public void add(EnvironmentEntry entry) {
            if (entry.getId() == null) {
                entry.assignIdentity(EnvironmentId.of(++sequence));
            }
            store.put(entry.getId().value(), entry);
        }

        @Override
        public Optional<EnvironmentEntry> findById(EnvironmentId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public List<EnvironmentEntry> findAll() {
            return List.copyOf(store.values());
        }

        @Override
        public List<EnvironmentEntry> findApplicableTo(ProjectId projectId) {
            return store.values().stream()
                    .filter(entry -> entry.getScope() == EnvironmentScope.GLOBAL
                            || entry.getProjectId().equals(projectId))
                    .toList();
        }

        @Override
        public boolean existsByKey(EnvironmentScope scope,
                                   ProjectId projectId,
                                   String key,
                                   EnvironmentId excludingId) {
            return store.values().stream()
                    .filter(entry -> excludingId == null || !entry.getId().equals(excludingId))
                    .anyMatch(entry -> entry.getScope() == scope
                            && java.util.Objects.equals(entry.getProjectId(), projectId)
                            && entry.getKey().equals(key));
        }

        @Override
        public void save(EnvironmentEntry entry) {
            add(entry);
        }

        @Override
        public void delete(EnvironmentId id) {
            store.remove(id.value());
        }
    }

    public static final class Tasks implements TaskRepository {

        private final Map<Long, Task> store = new LinkedHashMap<>();
        private Runs runs;
        public int saves;
        public int editableUpdates;
        public final List<TaskId> deleted = new ArrayList<>();
        private long sequence;

        public void wire(Runs runs) {
            this.runs = runs;
        }

        public void add(Task task) {
            store.put(task.getId().value(), task);
        }

        public Task get(long id) {
            return store.get(id);
        }

        @Override
        public Optional<Task> findById(TaskId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public Optional<Task> findByIdForUpdate(TaskId id) {
            return findById(id);
        }

        @Override
        public List<Task> findRunning() {
            return store.values().stream()
                    .filter(task -> task.getStatus() == TaskStatus.RUNNING)
                    .toList();
        }

        @Override
        public void save(Task task) {
            // 新录入的任务还没有 ID，替身在这里补一个 —— 真实仓储由数据库自增列回填，
            // 少了这一步，任何「录入后拿 ID」的用例都会在替身上撞空指针，而不是撞到真问题
            if (task.getId() == null) {
                task.assignIdentity(TaskId.of(++sequence));
            }
            store.put(task.getId().value(), task);
            saves++;
        }

        @Override
        public void updateEditable(Task task) {
            store.put(task.getId().value(), task);
            editableUpdates++;
        }

        @Override
        public void delete(TaskId id) {
            store.remove(id.value());
            deleted.add(id);
        }

        @Override
        public List<Task> findDispatchable(int limit) {
            return store.values().stream()
                    .filter(task -> task.getStatus() == TaskStatus.PENDING
                            || task.getStatus() == TaskStatus.QUEUED
                            || (task.getStatus() == TaskStatus.RUNNING && awaitingContinuation(task)))
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

        private boolean awaitingContinuation(Task task) {
            if (runs == null) {
                return false;
            }
            return runs.findLatestByTask(task.getId())
                    .map(run -> run.getStatus() == RunStatus.BLOCKED)
                    .orElse(false);
        }
    }

    public static final class Runs implements TaskRunRepository {

        private final Map<Long, TaskRun> store = new LinkedHashMap<>();
        private Tasks tasks;
        private long sequence;

        /** 接上任务仓储：租约是按项目算的，而项目挂在任务上，替身也得顺着这条线找。 */
        public void wire(Tasks tasks) {
            this.tasks = tasks;
        }

        public TaskRun get(long id) {
            return store.get(id);
        }

        public List<TaskRun> all() {
            return List.copyOf(store.values());
        }

        @Override
        public Optional<TaskRun> findById(TaskRunId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public Optional<TaskRun> findLatestByTask(TaskId taskId) {
            return store.values().stream()
                    .filter(run -> run.getTaskId().equals(taskId))
                    .max(Comparator.comparing(TaskRun::getAttemptNo));
        }

        @Override
        public List<TaskRun> listByTask(TaskId taskId) {
            return store.values().stream()
                    .filter(run -> run.getTaskId().equals(taskId))
                    .toList();
        }

        @Override
        public List<TaskRun> findRunningInternal() {
            return store.values().stream()
                    .filter(run -> run.getStatus() == RunStatus.RUNNING)
                    .filter(run -> TaskRun.EXECUTOR_INTERNAL.equals(run.getExecutor()))
                    .toList();
        }

        @Override
        public boolean hasActiveLease(ProjectId projectId, Instant now) {
            return store.values().stream().anyMatch(run -> run.hasLease() && !run.isLeaseExpiredAt(now)
                    && tasks != null && tasks.findById(run.getTaskId())
                            .map(task -> task.getProjectId().equals(projectId))
                            .orElse(false));
        }

        @Override
        public List<TaskRun> findExpiredLeases(Instant now) {
            return store.values().stream()
                    .filter(run -> run.getStatus() == RunStatus.RUNNING && run.isLeaseExpiredAt(now))
                    .toList();
        }

        @Override
        public List<TaskRun> findActiveLeases(Instant now) {
            return store.values().stream()
                    .filter(run -> run.getStatus() == RunStatus.RUNNING && run.hasLease())
                    .filter(run -> !run.isLeaseExpiredAt(now))
                    .toList();
        }

        @Override
        public void renewLease(TaskRunId runId, Instant leaseExpiresAt) {
            // 续租不动替身：到期时间是 TaskRun.renewLease 在内存里改的，这里再改一遍是重复
        }

        @Override
        public void save(TaskRun run) {
            if (run.getId() == null) {
                run.assignIdentity(TaskRunId.of(++sequence));
            }
            store.put(run.getId().value(), run);
        }
    }

    public static final class Events implements RunEventRepository {

        private final Map<Long, List<RunEvent>> store = new HashMap<>();
        private Runs runs;

        /** 接上执行记录仓储：事件的外键指向它，先跑后写会让事件无处挂靠。 */
        public void wire(Runs runs) {
            this.runs = runs;
        }

        @Override
        public void append(TaskRunId runId, RunEvent event) {
            if (runs != null && runs.findById(runId).isEmpty()) {
                throw new DomainException("事件流没有挂靠点，执行记录还没落库：" + runId);
            }
            store.computeIfAbsent(runId.value(), key -> new ArrayList<>()).add(event);
        }

        @Override
        public List<RunEvent> findByRun(TaskRunId runId) {
            return List.copyOf(store.getOrDefault(runId.value(), List.of()));
        }

        public List<RunEvent> all() {
            return store.values().stream().flatMap(List::stream).toList();
        }
    }

    public static final class Decisions implements DecisionRepository {

        private final Map<Long, Decision> store = new LinkedHashMap<>();
        private long sequence;
        public int saves;

        public Decision get(long id) {
            return store.get(id);
        }

        public List<Decision> all() {
            return List.copyOf(store.values());
        }

        @Override
        public Optional<Decision> findById(DecisionId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public void save(Decision decision) {
            if (decision.getId() == null) {
                decision.assignIdentity(DecisionId.of(++sequence));
            }
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
    }

    /** 只记账的缺陷仓储：验证执行器上报的缺陷确实落成独立聚合。 */
    public static final class Bugs implements BugRepository {

        private final Map<Long, Bug> store = new LinkedHashMap<>();
        private long sequence;

        public List<Bug> all() {
            return List.copyOf(store.values());
        }

        @Override
        public Optional<Bug> findById(BugId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public Optional<Bug> findByIdForUpdate(BugId id) {
            return findById(id);
        }

        @Override
        public void save(Bug bug) {
            if (bug.getId() == null) {
                bug.assignIdentity(BugId.of(++sequence));
            }
            store.put(bug.getId().value(), bug);
        }
    }

    /** 只记账的 Bug 附件仓储。 */
    public static final class BugAttachments implements BugAttachmentRepository {

        private final Map<Long, BugAttachment> store = new LinkedHashMap<>();
        private long sequence;

        @Override
        public BugAttachment save(BugAttachment attachment) {
            BugAttachment persisted = attachment.withIdentity(BugAttachmentId.of(++sequence));
            store.put(persisted.id().value(), persisted);
            return persisted;
        }

        @Override
        public Optional<BugAttachment> findById(BugAttachmentId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public List<BugAttachment> findByBug(BugId bugId) {
            return store.values().stream().filter(item -> item.bugId().equals(bugId)).toList();
        }
    }

    public static final class Rules implements DecisionRuleRepository {

        private final List<DecisionRule> rules = new ArrayList<>();

        public Rules with(DecisionRule rule) {
            rules.add(rule);
            return this;
        }

        @Override
        public List<DecisionRule> findEnabledFor(Long projectId) {
            return rules.stream()
                    .filter(rule -> rule.enabled())
                    .filter(rule -> rule.isGlobal() || rule.projectId().equals(projectId))
                    .toList();
        }
    }

    /** 只记账的附件仓储：ID 由替身发号，断言看的是「存了什么」。 */
    public static final class Attachments implements TaskAttachmentRepository {

        private final Map<Long, TaskAttachment> store = new LinkedHashMap<>();
        private long sequence;

        @Override
        public TaskAttachment save(TaskAttachment attachment) {
            TaskAttachment persisted = attachment.withIdentity(AttachmentId.of(++sequence));
            store.put(persisted.id().value(), persisted);
            return persisted;
        }

        @Override
        public Optional<TaskAttachment> findById(AttachmentId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public List<TaskAttachment> findByTask(TaskId taskId) {
            return store.values().stream().filter(item -> item.taskId().equals(taskId)).toList();
        }
    }

    public static final class TaskPlugins implements TaskPluginRepository {

        private final Map<Long, List<PluginBinding>> store = new HashMap<>();

        public void add(TaskId taskId, PluginBinding... bindings) {
            store.put(taskId.value(), List.of(bindings));
        }

        @Override
        public List<PluginBinding> findByTask(TaskId taskId) {
            return List.copyOf(store.getOrDefault(taskId.value(), List.of()));
        }

        @Override
        public void replace(TaskId taskId, List<PluginBinding> bindings) {
            store.put(taskId.value(), List.copyOf(bindings));
        }

        public static PluginBinding plugin(long id, String name, int revision, String content) {
            return new PluginBinding(
                    PluginId.of(id),
                    name,
                    revision,
                    "capability.txt",
                    "a".repeat(64),
                    content,
                    true,
                    Instant.parse("2026-10-17T00:00:00Z"));
        }
    }

    /** 内存存储：不碰磁盘，字节留在 map 里。路径形状照抄本地实现（UUID + 扩展名）。 */
    public static final class AttachmentFiles implements AttachmentStorage {

        public final Map<String, byte[]> stored = new LinkedHashMap<>();
        private long sequence;

        @Override
        public String store(AttachmentScope scope, long ownerId, String extension, byte[] content) {
            String path = "/tmp/regent-fake-attachments/"
                    + scope.name().toLowerCase(java.util.Locale.ROOT) + "-" + ownerId
                    + "/f" + (++sequence) + extension;
            stored.put(path, content);
            return path;
        }

        @Override
        public byte[] read(String storagePath) {
            byte[] content = stored.get(storagePath);
            if (content == null) {
                throw new EntityNotFoundException("附件文件不在了：" + storagePath);
            }
            return content;
        }

        @Override
        public void delete(String storagePath) {
            stored.remove(storagePath);
        }
    }

    public static final class Projects implements ProjectProfileRepository {

        private final Map<Long, ProjectProfile> store = new HashMap<>();

        public Projects put(ProjectProfile profile) {
            store.put(profile.id().value(), profile);
            return this;
        }

        public static ProjectProfile profile(long id, DecisionLevel threshold, boolean enabled) {
            return new ProjectProfile(ProjectId.of(id), "p" + id, "项目 " + id,
                    "/tmp/regent-project-" + id, threshold, enabled);
        }

        @Override
        public Optional<ProjectProfile> findById(ProjectId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public Optional<ProjectProfile> findByCode(String code) {
            return store.values().stream().filter(profile -> profile.code().equals(code)).findFirst();
        }

        @Override
        public List<ProjectProfile> findAll() {
            return store.values().stream()
                    .sorted(Comparator.comparingLong(profile -> profile.id().value()))
                    .toList();
        }
    }

    /** 事务替身：可直接执行，也可把提交后动作暂存下来单独验证启动时机。 */
    public static final class Transactions implements TransactionRunner {

        private final List<Runnable> afterCommit = new ArrayList<>();
        private final List<Runnable> afterRollback = new ArrayList<>();
        public boolean deferAfterCommit;
        public int transactions;

        @Override
        public <T> T inTransaction(Supplier<T> action) {
            transactions++;
            return action.get();
        }

        @Override
        public void afterCompletion(Runnable onCommit, Runnable onRollback) {
            if (deferAfterCommit) {
                afterCommit.add(onCommit);
                afterRollback.add(onRollback);
                return;
            }
            onCommit.run();
        }

        public void runAfterCommit() {
            List.copyOf(afterCommit).forEach(Runnable::run);
            afterCommit.clear();
            afterRollback.clear();
        }

        public void runAfterRollback() {
            List.copyOf(afterRollback).forEach(Runnable::run);
            afterCommit.clear();
            afterRollback.clear();
        }
    }

    /**
     * 只记账、不真跑的工作线程池：单测要断言的是「提交了什么」，不是并发本身。
     * 需要让它真的跑起来时调 {@link #runAll()} —— 不自动跑，是为了让「提交时机」与
     * 「执行结果」在断言里可以分开观察。
     */
    public static final class Workers extends AbstractExecutorService {

        private final List<Runnable> submitted = new ArrayList<>();
        public boolean rejected;

        @Override
        public void execute(Runnable command) {
            if (rejected) {
                throw new java.util.concurrent.RejectedExecutionException("测试要求拒绝");
            }
            submitted.add(command);
        }

        public void runAll() {
            List.copyOf(submitted).forEach(Runnable::run);
        }

        public int size() {
            return submitted.size();
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }

    /** 造一个已经落库的任务：ID 回填过，状态由调用方给。 */
    public static Task task(long id, ProjectId projectId, TaskStatus status) {
        Task task = Task.manual(projectId, "任务 " + id, "把这件事做完", 0);
        task.assignIdentity(TaskId.of(id));
        if (status == TaskStatus.QUEUED) {
            task.queue();
        }
        if (status == TaskStatus.RUNNING || status == TaskStatus.BLOCKED) {
            task.dispatch("codex/task-" + id);
        }
        if (status == TaskStatus.BLOCKED) {
            task.block(DecisionId.of(999));
        }
        if (status == TaskStatus.DONE) {
            task.dispatch("codex/task-" + id);
            task.complete();
        }
        if (task.getStatus() != status) {
            throw new DomainException("测试数据构造不出来目标状态：" + status);
        }
        return task;
    }

    /** 让一个任务带上会话，模拟「已经跑过一轮」——续跑的前提。 */
    public static Task withThread(Task task, String threadId) {
        task.recordThreadId(threadId);
        return task;
    }
}
