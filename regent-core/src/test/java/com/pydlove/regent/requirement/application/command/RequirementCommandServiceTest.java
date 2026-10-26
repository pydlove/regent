package com.pydlove.regent.requirement.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskSource;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.requirement.domain.model.Requirement;
import com.pydlove.regent.requirement.domain.model.RequirementId;
import com.pydlove.regent.requirement.domain.model.RequirementTrashItem;
import com.pydlove.regent.requirement.domain.model.RequirementPlanningRequest;
import com.pydlove.regent.requirement.domain.model.RequirementPlanningResult;
import com.pydlove.regent.requirement.domain.model.RequirementTaskDraft;
import com.pydlove.regent.requirement.domain.port.RequirementPlanner;
import com.pydlove.regent.requirement.domain.repository.RequirementRepository;
import com.pydlove.regent.requirement.domain.repository.TaskDependencyRepository;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RequirementCommandServiceTest {

    private static final UserId OWNER = UserId.of(1L);
    private static final ProjectId PROJECT = ProjectId.of(7L);

    @Test
    @DisplayName("需求确认后事务性创建任务并保存依赖")
    void confirmsIntoTasksAndDependencies() {
        Requirements requirements = new Requirements();
        ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
        Dependencies dependencies = new Dependencies();
        Projects projects = new Projects();
        RequirementPlanner planner = request -> new RequirementPlanningResult(
                List.of(),
                List.of("沿用现有部署配置"),
                List.of(
                        draft("T1", List.of()),
                        draft("T2", List.of("T1"))));
        RequirementCommandService service = new RequirementCommandService(
                requirements,
                tasks,
                dependencies,
                projects,
                planner,
                new DirectTransactions());

        Requirement created = service.create(new SaveRequirementCommand(
                PROJECT,
                "部署多项目",
                "部署需要支持多个项目",
                "编辑后仍然可以筛选",
                null), OWNER);
        Requirement analyzed = service.analyze(created.getId(), OWNER);
        assertEquals(2, analyzed.getTaskDrafts().size());
        Requirement confirmed = service.confirm(created.getId(), analyzed.getTaskDrafts(), OWNER);

        assertNotNull(confirmed.getId());
        List<Task> createdTasks = tasks.findByRequirement(confirmed.getId());
        assertEquals(2, createdTasks.size());
        assertEquals(TaskSource.REQUIREMENT, createdTasks.get(0).getSource());
        assertEquals(List.of(createdTasks.get(0).getId()),
                dependencies.findDependencies(createdTasks.get(1).getId()));
    }

    private static RequirementTaskDraft draft(String key, List<String> dependencies) {
        return new RequirementTaskDraft(
                key,
                "任务 " + key,
                "完成 " + key,
                List.of("范围"),
                List.of(),
                List.of("验收通过"),
                dependencies,
                0,
                "L1",
                1);
    }

    private static final class Requirements implements RequirementRepository {

        private final Map<Long, Requirement> store = new LinkedHashMap<>();
        private long sequence;

        @Override
        public Optional<Requirement> findById(RequirementId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public List<Requirement> findByProject(ProjectId projectId) {
            return store.values().stream()
                    .filter(requirement -> requirement.getProjectId().equals(projectId))
                    .toList();
        }

        @Override
        public List<Requirement> findAll(UserId ownerId) {
            return List.copyOf(store.values());
        }

        @Override
        public List<RequirementTrashItem> findDeletedByProject(ProjectId projectId, UserId ownerId) {
            return List.of();
        }

        @Override
        public Optional<RequirementTrashItem> findDeletedByIdOwned(RequirementId id,
                                                                   UserId ownerId) {
            return Optional.empty();
        }

        @Override
        public void moveToTrash(RequirementId id, UserId deletedBy, Instant deletedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void restore(RequirementId id, Instant updatedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void save(Requirement requirement) {
            if (requirement.getId() == null) {
                requirement.assignIdentity(RequirementId.of(++sequence));
            }
            store.put(requirement.getId().value(), requirement);
        }
    }

    private static final class Dependencies implements TaskDependencyRepository {

        private final Map<Long, List<TaskId>> store = new LinkedHashMap<>();

        @Override
        public void replace(TaskId taskId, Collection<TaskId> dependsOnTaskIds) {
            store.put(taskId.value(), List.copyOf(dependsOnTaskIds));
        }

        @Override
        public List<TaskId> findDependencies(TaskId taskId) {
            return store.getOrDefault(taskId.value(), List.of());
        }
    }

    private static final class Projects implements ProjectProfileRepository {

        private final ProjectProfile profile = new ProjectProfile(
                PROJECT, "regent", "Regent", "/tmp/regent",
                DecisionLevel.L1, true);

        @Override
        public Optional<ProjectProfile> findById(ProjectId id) {
            return id.equals(PROJECT) ? Optional.of(profile) : Optional.empty();
        }

        @Override
        public Optional<ProjectProfile> findByCode(String code) {
            return "regent".equals(code) ? Optional.of(profile) : Optional.empty();
        }

        @Override
        public List<ProjectProfile> findAll() {
            return List.of(profile);
        }
    }

    private static final class DirectTransactions implements TransactionRunner {

        @Override
        public <T> T inTransaction(Supplier<T> action) {
            return action.get();
        }

        @Override
        public void afterCompletion(Runnable onCommit, Runnable onRollback) {
            onCommit.run();
        }
    }
}
