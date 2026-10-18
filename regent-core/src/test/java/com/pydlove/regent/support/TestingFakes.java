package com.pydlove.regent.support;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.testing.domain.model.TestCase;
import com.pydlove.regent.testing.domain.model.TestCaseBinding;
import com.pydlove.regent.testing.domain.model.TestCaseId;
import com.pydlove.regent.testing.domain.model.TestCaseRun;
import com.pydlove.regent.testing.domain.model.TestCaseRunId;
import com.pydlove.regent.testing.domain.repository.TaskTestCaseRepository;
import com.pydlove.regent.testing.domain.repository.TestCaseRepository;
import com.pydlove.regent.testing.domain.repository.TestCaseRunRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/** 测试用例上下文的内存替身。 */
public final class TestingFakes {

    private TestingFakes() {
    }

    public static final class TestCases implements TestCaseRepository {

        private final Map<Long, TestCase> store = new LinkedHashMap<>();
        private long sequence;

        public TestCase put(TestCase testCase) {
            if (testCase.getId() == null) {
                testCase.assignIdentity(TestCaseId.of(++sequence));
            }
            store.put(testCase.getId().value(), testCase);
            return testCase;
        }

        @Override
        public Optional<TestCase> findById(TestCaseId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public Optional<TestCase> findByIdForUpdate(TestCaseId id) {
            return findById(id);
        }

        @Override
        public List<TestCase> findAll() {
            return List.copyOf(store.values());
        }

        @Override
        public List<TestCase> findByProject(ProjectId projectId) {
            return store.values().stream()
                    .filter(testCase -> testCase.getProjectId().equals(projectId))
                    .toList();
        }

        @Override
        public boolean existsByName(ProjectId projectId, String name, TestCaseId excludingId) {
            return store.values().stream()
                    .anyMatch(testCase -> testCase.getProjectId().equals(projectId)
                            && testCase.getName().equals(name)
                            && (excludingId == null || !testCase.getId().equals(excludingId)));
        }

        @Override
        public void save(TestCase testCase) {
            put(testCase);
        }

        @Override
        public void delete(TestCaseId id) {
            store.remove(id.value());
        }
    }

    public static final class Bindings implements TaskTestCaseRepository {

        private final Map<Long, List<TestCaseBinding>> store = new LinkedHashMap<>();

        public void put(TaskId taskId, TestCaseBinding binding) {
            store.computeIfAbsent(taskId.value(), ignored -> new ArrayList<>()).add(binding);
        }

        @Override
        public List<TestCaseBinding> findByTask(TaskId taskId) {
            return List.copyOf(store.getOrDefault(taskId.value(), List.of()));
        }

        @Override
        public void replace(TaskId taskId, List<TestCaseBinding> bindings) {
            store.put(taskId.value(), new ArrayList<>(bindings));
        }
    }

    public static final class Runs implements TestCaseRunRepository {

        private final Map<Long, TestCaseRun> store = new LinkedHashMap<>();
        private long sequence;

        @Override
        public Optional<TestCaseRun> findById(TestCaseRunId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public List<TestCaseRun> findByTask(TaskId taskId) {
            return store.values().stream()
                    .filter(run -> run.getTaskId().filter(taskId::equals).isPresent())
                    .sorted((left, right) -> Long.compare(right.getId().value(), left.getId().value()))
                    .toList();
        }

        @Override
        public List<TestCaseRun> findByTestCase(TestCaseId testCaseId, int limit) {
            return store.values().stream()
                    .filter(run -> run.getTestCaseId().equals(testCaseId))
                    .sorted((left, right) -> Long.compare(right.getId().value(), left.getId().value()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<TestCaseRun> findLatestByTestCaseIds(List<TestCaseId> testCaseIds) {
            List<TestCaseRun> latest = new ArrayList<>();
            for (TestCaseId testCaseId : testCaseIds) {
                store.values().stream()
                        .filter(run -> run.getTestCaseId().equals(testCaseId))
                        .max((left, right) -> Long.compare(left.getId().value(), right.getId().value()))
                        .ifPresent(latest::add);
            }
            return latest;
        }

        @Override
        public List<TestCaseRun> findActive() {
            return store.values().stream()
                    .filter(run -> !run.getStatus().isFinished())
                    .toList();
        }

        @Override
        public boolean existsByTask(TaskId taskId) {
            return store.values().stream()
                    .anyMatch(run -> run.getTaskId().filter(taskId::equals).isPresent());
        }

        @Override
        public void save(TestCaseRun run) {
            if (run.getId() == null) {
                run.assignIdentity(TestCaseRunId.of(++sequence));
            }
            store.put(run.getId().value(), run);
        }

        public List<TestCaseRun> all() {
            return List.copyOf(store.values());
        }
    }

    /** 测试线程池的直接执行替身，避免单测等待异步调度。 */
    public static final class DirectExecutorService extends AbstractExecutorService {

        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
