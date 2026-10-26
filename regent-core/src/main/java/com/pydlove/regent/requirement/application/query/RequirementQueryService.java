package com.pydlove.regent.requirement.application.query;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.Requirement;
import com.pydlove.regent.requirement.domain.model.RequirementId;
import com.pydlove.regent.requirement.domain.model.RequirementTrashItem;
import com.pydlove.regent.requirement.domain.model.RequirementStatus;
import com.pydlove.regent.requirement.domain.repository.RequirementRepository;
import com.pydlove.regent.requirement.domain.repository.TaskDependencyRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 需求列表与详情查询。 */
public class RequirementQueryService {

    private final RequirementRepository requirementRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository dependencyRepository;

    public RequirementQueryService(RequirementRepository requirementRepository,
                                   TaskRepository taskRepository,
                                   TaskDependencyRepository dependencyRepository) {
        this.requirementRepository = requirementRepository;
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
    }

    public List<RequirementView> list(ProjectId projectId, UserId ownerId) {
        List<Requirement> requirements = projectId == null
                ? requirementRepository.findAll(ownerId)
                : requirementRepository.findByProject(projectId, ownerId);
        return requirements.stream().map(this::view).toList();
    }

    public RequirementView get(RequirementId id, UserId ownerId) {
        return view(requirementRepository.findByIdOwned(id, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("需求不存在：" + id.value())));
    }

    public List<RequirementTrashItem> trash(ProjectId projectId, UserId ownerId) {
        return requirementRepository.findDeletedByProject(projectId, ownerId);
    }

    private RequirementView view(Requirement requirement) {
        List<Task> tasks = taskRepository.findByRequirement(requirement.getId());
        Map<Long, List<Long>> dependencies = new LinkedHashMap<>();
        for (Task task : tasks) {
            dependencies.put(task.getId().value(), dependencyRepository
                    .findDependencies(task.getId()).stream()
                    .map(id -> id.value())
                    .toList());
        }
        return new RequirementView(
                requirement,
                effectiveStatus(requirement, tasks),
                tasks,
                dependencies);
    }

    private static RequirementStatus effectiveStatus(Requirement requirement, List<Task> tasks) {
        if (requirement.getStatus() != RequirementStatus.PLANNED || tasks.isEmpty()) {
            return requirement.getStatus();
        }
        long done = tasks.stream().filter(task -> task.getStatus() == TaskStatus.DONE).count();
        boolean active = tasks.stream().anyMatch(task ->
                task.getStatus() == TaskStatus.RUNNING
                        || task.getStatus() == TaskStatus.QUEUED
                        || task.getStatus() == TaskStatus.BLOCKED);
        boolean terminal = tasks.stream().allMatch(task ->
                task.getStatus() == TaskStatus.DONE
                        || task.getStatus() == TaskStatus.FAILED
                        || task.getStatus() == TaskStatus.CANCELLED);
        if (done == tasks.size()) return RequirementStatus.DONE;
        if (terminal) return done > 0 ? RequirementStatus.PARTIAL : RequirementStatus.PLANNED;
        return active ? RequirementStatus.RUNNING : RequirementStatus.PLANNED;
    }
}
