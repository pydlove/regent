package com.pydlove.regent.requirement.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.delivery.domain.model.Initiative;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.InitiativeStatus;
import com.pydlove.regent.delivery.domain.model.DeliveryStage;
import com.pydlove.regent.delivery.domain.model.ProductVersion;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.delivery.domain.model.ProductVersionStatus;
import com.pydlove.regent.delivery.domain.model.RequirementDelivery;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.delivery.domain.repository.ProductVersionRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDeliveryRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.query.ProjectMemberAccessService;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.requirement.domain.model.Requirement;
import com.pydlove.regent.requirement.domain.model.RequirementId;
import com.pydlove.regent.requirement.domain.model.RequirementSource;
import com.pydlove.regent.requirement.domain.model.RequirementPlanningRequest;
import com.pydlove.regent.requirement.domain.model.RequirementPlanningResult;
import com.pydlove.regent.requirement.domain.model.RequirementTaskDraft;
import com.pydlove.regent.requirement.domain.model.RequirementTrashItem;
import com.pydlove.regent.requirement.domain.port.RequirementPlanner;
import com.pydlove.regent.requirement.domain.port.RequirementPurgePort;
import com.pydlove.regent.requirement.domain.repository.RequirementRepository;
import com.pydlove.regent.requirement.domain.repository.TaskDependencyRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.shared.domain.port.TaskStandardBinder;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.time.Instant;

/** 需求创建、澄清、拆解和确认创建任务。 */
public class RequirementCommandService {

    private final RequirementRepository requirementRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository dependencyRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final RequirementPlanner planner;
    private final InitiativeRepository initiativeRepository;
    private final ProductVersionRepository productVersionRepository;
    private final RequirementDeliveryRepository deliveryRepository;
    private final ProjectMemberAccessService memberAccessService;
    private final TransactionRunner transactionRunner;
    private final TaskStandardBinder taskStandardBinder;
    private final RequirementPurgePort requirementPurgePort;

    public RequirementCommandService(RequirementRepository requirementRepository,
                                     TaskRepository taskRepository,
                                     TaskDependencyRepository dependencyRepository,
                                     ProjectProfileRepository projectProfileRepository,
                                     RequirementPlanner planner,
                                     TransactionRunner transactionRunner) {
        this(requirementRepository, taskRepository, dependencyRepository,
                projectProfileRepository, planner, transactionRunner, null, null,
                null, null, null, null);
    }

    public RequirementCommandService(RequirementRepository requirementRepository,
                                     TaskRepository taskRepository,
                                     TaskDependencyRepository dependencyRepository,
                                     ProjectProfileRepository projectProfileRepository,
                                     RequirementPlanner planner,
                                     TransactionRunner transactionRunner,
                                     TaskStandardBinder taskStandardBinder) {
        this(requirementRepository, taskRepository, dependencyRepository,
                projectProfileRepository, planner, transactionRunner, taskStandardBinder, null,
                null, null, null, null);
    }

    public RequirementCommandService(RequirementRepository requirementRepository,
                                     TaskRepository taskRepository,
                                     TaskDependencyRepository dependencyRepository,
                                     ProjectProfileRepository projectProfileRepository,
                                     RequirementPlanner planner,
                                     TransactionRunner transactionRunner,
                                     TaskStandardBinder taskStandardBinder,
                                     InitiativeRepository initiativeRepository,
                                     ProductVersionRepository productVersionRepository,
                                     RequirementDeliveryRepository deliveryRepository,
                                     ProjectMemberAccessService memberAccessService,
                                     RequirementPurgePort requirementPurgePort) {
        this.requirementRepository = requirementRepository;
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.planner = planner;
        this.transactionRunner = transactionRunner;
        this.taskStandardBinder = taskStandardBinder;
        this.initiativeRepository = initiativeRepository;
        this.productVersionRepository = productVersionRepository;
        this.deliveryRepository = deliveryRepository;
        this.memberAccessService = memberAccessService;
        this.requirementPurgePort = requirementPurgePort;
    }

    public Requirement create(SaveRequirementCommand command, UserId ownerId) {
        return transactionRunner.inTransaction(() -> {
            ProjectProfile project = requireProject(command.projectId(), ownerId);
            InitiativeId initiativeId = requireInitiative(
                    command.initiativeId(), command.projectId());
            Requirement requirement = Requirement.create(
                    project.id(),
                    initiativeId,
                    command.title(),
                    command.rawText(),
                    command.acceptanceText(),
                    command.clarificationText(),
                    command.source() == null ? RequirementSource.MANUAL : command.source(),
                    ownerId);
            if (command.versionId() != null) {
                requireProductPlanner(project.id(), ownerId);
                assignVersionTo(requirement, command.versionId());
            }
            requirement.assignNumber(requirementRepository.nextRequirementNo());
            requirementRepository.save(requirement);
            return requirement;
        });
    }

    public Requirement update(RequirementId id,
                              SaveRequirementCommand command,
                              UserId ownerId) {
        Requirement requirement = load(id, ownerId);
        requireProject(command.projectId(), ownerId);
        if (!requirement.getProjectId().equals(command.projectId())) {
            throw new com.pydlove.regent.shared.domain.DomainException("需求创建后不能更换归属项目");
        }
        if (command.initiativeId() != null
                && !command.initiativeId().equals(requirement.getInitiativeId())) {
            throw new com.pydlove.regent.shared.domain.DomainException("需求创建后不能更换所属立项");
        }
        requirement.updateDraft(command.title(), command.rawText(),
                command.acceptanceText(), command.clarificationText());
        requirementRepository.save(requirement);
        return requirement;
    }

    public Requirement analyze(RequirementId id, UserId ownerId) {
        Requirement requirement = load(id, ownerId);
        ProjectProfile project = requireProject(requirement.getProjectId(), ownerId);
        if (project.path() == null) {
            throw new com.pydlove.regent.shared.domain.DomainException(
                    "项目尚未绑定本机代码目录，暂时不能进行 AI 需求分析");
        }
        RequirementPlanningResult result = planner.analyze(new RequirementPlanningRequest(
                project.id(),
                project.code(),
                project.name(),
                project.path(),
                project.summary(),
                requirement.getTitle(),
                requirement.getRawText(),
                requirement.getAcceptanceText(),
                requirement.getClarificationText()));
        requirement.applyAnalysis(result.questions(), result.assumptions(), result.taskDrafts());
        requirementRepository.save(requirement);
        return requirement;
    }

    public Requirement confirm(RequirementId id,
                               List<RequirementTaskDraft> drafts,
                               UserId ownerId) {
        return transactionRunner.inTransaction(() -> {
            Requirement requirement = load(id, ownerId);
            ProjectProfile project = requireProject(requirement.getProjectId(), ownerId);
            if (drafts != null && !drafts.isEmpty()) {
                requirement.replaceTaskDrafts(drafts);
            }
            Map<String, Task> createdByKey = new LinkedHashMap<>();
            for (RequirementTaskDraft draft : requirement.getTaskDrafts()) {
                Task task = Task.fromRequirement(
                        project.id(),
                        requirement.getId(),
                        draft.title(),
                        prompt(draft),
                        draft.priority());
                taskRepository.save(task);
                if (taskStandardBinder != null) {
                    taskStandardBinder.bindDefaults(task.getId().value(), project.id().value());
                }
                createdByKey.put(draft.key(), task);
            }
            for (RequirementTaskDraft draft : requirement.getTaskDrafts()) {
                Task task = createdByKey.get(draft.key());
                List<com.pydlove.regent.execution.domain.model.TaskId> dependencies =
                        draft.dependencies().stream()
                                .map(createdByKey::get)
                                .filter(java.util.Objects::nonNull)
                                .map(Task::getId)
                                .toList();
                dependencyRepository.replace(task.getId(), dependencies);
            }
            requirement.confirm();
            requirementRepository.save(requirement);
            return requirement;
        });
    }

    public Requirement cancel(RequirementId id, UserId ownerId) {
        Requirement requirement = load(id, ownerId);
        requirement.cancel();
        requirementRepository.save(requirement);
        return requirement;
    }

    public Requirement assignVersion(RequirementId id,
                                     ProductVersionId versionId,
                                     UserId ownerId) {
        Requirement requirement = load(id, ownerId);
        requireProductPlanner(requirement.getProjectId(), ownerId);
        assignVersionTo(requirement, versionId);
        requirementRepository.save(requirement);
        return requirement;
    }

    private void assignVersionTo(Requirement requirement, ProductVersionId versionId) {
        ProductVersion version = requirePlannedVersion(versionId, requirement.getProjectId());
        Initiative versionInitiative = initiativeRepository == null
                ? null
                : initiativeRepository.findByVersion(version.getId()).orElse(null);
        if (versionInitiative == null || versionInitiative.getStatus() != InitiativeStatus.APPROVED) {
            throw new com.pydlove.regent.shared.domain.DomainException(
                    "请先完成该产品版本的立项审批，再纳入需求");
        }
        requirement.assignVersion(version.getId(), versionInitiative.getId());
    }

    public Requirement removeVersion(RequirementId id, UserId ownerId) {
        Requirement requirement = load(id, ownerId);
        requireProductPlanner(requirement.getProjectId(), ownerId);
        requirement.removeFromVersion();
        requirementRepository.save(requirement);
        return requirement;
    }

    public void moveToTrash(RequirementId id, UserId ownerId) {
        Requirement requirement = load(id, ownerId);
        if (!isProjectOwner(requirement, ownerId)) {
            requirement.requireTrashable();
        }
        requirementRepository.moveToTrash(id, ownerId, Instant.now());
    }

    public int moveManyToTrash(List<RequirementId> ids, UserId ownerId) {
        if (ids == null || ids.isEmpty()) {
            throw new com.pydlove.regent.shared.domain.DomainException("请选择要删除的需求");
        }
        List<RequirementId> uniqueIds = List.copyOf(new LinkedHashSet<>(ids));
        return transactionRunner.inTransaction(() -> {
            List<Requirement> requirements = uniqueIds.stream()
                    .map(id -> load(id, ownerId))
                    .toList();
            com.pydlove.regent.project.domain.model.ProjectId projectId =
                    requirements.get(0).getProjectId();
            if (requirements.stream()
                    .anyMatch(requirement -> !projectId.equals(requirement.getProjectId()))) {
                throw new com.pydlove.regent.shared.domain.DomainException(
                        "一次只能批量删除同一个项目的需求");
            }
            requireProjectOwner(projectId, ownerId);
            Instant deletedAt = Instant.now();
            for (RequirementId id : uniqueIds) {
                requirementRepository.moveToTrash(id, ownerId, deletedAt);
            }
            return uniqueIds.size();
        });
    }

    public int purgeFromTrash(List<RequirementId> ids, UserId ownerId) {
        if (ids == null || ids.isEmpty()) {
            throw new com.pydlove.regent.shared.domain.DomainException("请选择要彻底删除的需求");
        }
        List<RequirementId> uniqueIds = List.copyOf(new LinkedHashSet<>(ids));
        return transactionRunner.inTransaction(() -> {
            List<RequirementTrashItem> items = uniqueIds.stream()
                    .map(id -> requirementRepository.findDeletedByIdOwned(id, ownerId)
                            .orElseThrow(() -> new EntityNotFoundException(
                                    "回收站中不存在该需求：" + id.value())))
                    .toList();
            com.pydlove.regent.project.domain.model.ProjectId projectId =
                    items.get(0).requirement().getProjectId();
            if (items.stream().anyMatch(item ->
                    !projectId.equals(item.requirement().getProjectId()))) {
                throw new com.pydlove.regent.shared.domain.DomainException(
                        "一次只能彻底删除同一个项目的需求");
            }
            requireProjectOwner(projectId, ownerId);
            if (requirementPurgePort == null) {
                throw new com.pydlove.regent.shared.domain.DomainException(
                        "当前环境未启用需求永久删除");
            }
            int purged = requirementPurgePort.purge(projectId, uniqueIds);
            if (purged != uniqueIds.size()) {
                throw new com.pydlove.regent.shared.domain.DomainException(
                        "部分需求永久删除失败，请刷新回收站后重试");
            }
            return purged;
        });
    }

    public Requirement restoreFromTrash(RequirementId id, UserId ownerId) {
        RequirementTrashItem item = requirementRepository.findDeletedByIdOwned(id, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("回收站中不存在该需求：" + id.value()));
        requirementRepository.restore(id, Instant.now());
        return load(id, ownerId);
    }

    public Requirement submitPoolReview(RequirementId id, UserId ownerId) {
        Requirement requirement = load(id, ownerId);
        requireProductPlanner(requirement.getProjectId(), ownerId);
        requireVersionFor(requirement);
        requirement.submitPoolReview();
        requirementRepository.save(requirement);
        return requirement;
    }

    public Requirement approvePoolReview(RequirementId id,
                                         UserId ownerId,
                                         String comment) {
        return transactionRunner.inTransaction(() -> {
            Requirement requirement = load(id, ownerId);
            requireProductReviewer(requirement.getProjectId(), ownerId);
            requireVersionFor(requirement);
            requirement.approvePoolReview(ownerId, comment);
            requirementRepository.save(requirement);
            createFormalDelivery(requirement, ownerId);
            return requirement;
        });
    }

    public Requirement rejectPoolReview(RequirementId id,
                                        UserId ownerId,
                                        String comment) {
        Requirement requirement = load(id, ownerId);
        requireProductReviewer(requirement.getProjectId(), ownerId);
        requireVersionFor(requirement);
        requirement.rejectPoolReview(ownerId, comment);
        requirementRepository.save(requirement);
        return requirement;
    }

    public Requirement approveOfflineReview(RequirementId id,
                                            UserId ownerId,
                                            String comment) {
        return transactionRunner.inTransaction(() -> {
            Requirement requirement = load(id, ownerId);
            requireProductReviewer(requirement.getProjectId(), ownerId);
            requireVersionFor(requirement);
            requirement.approveOfflineReview(ownerId, comment);
            requirementRepository.save(requirement);
            createFormalDelivery(requirement, ownerId);
            return requirement;
        });
    }

    private ProductVersion requirePlannedVersion(ProductVersionId id,
                                                 com.pydlove.regent.project.domain.model.ProjectId projectId) {
        if (productVersionRepository == null) {
            throw new com.pydlove.regent.shared.domain.DomainException(
                    "当前环境未启用产品版本规划");
        }
        ProductVersion version = productVersionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "产品版本不存在：" + id.value()));
        if (!version.getProjectId().equals(projectId)) {
            throw new com.pydlove.regent.shared.domain.DomainException(
                    "需求与产品版本不属于同一个项目");
        }
        if (version.getStatus() != ProductVersionStatus.PLANNING) {
            throw new com.pydlove.regent.shared.domain.DomainException(
                    "只有规划中的产品版本可以纳入需求");
        }
        return version;
    }

    private ProductVersion requireVersionFor(Requirement requirement) {
        if (requirement.getVersionId() == null) {
            throw new com.pydlove.regent.shared.domain.DomainException(
                    "需求加入产品版本后才能提交评审");
        }
        if (productVersionRepository == null) {
            throw new com.pydlove.regent.shared.domain.DomainException(
                    "当前环境未启用产品版本规划");
        }
        ProductVersion version = productVersionRepository.findById(requirement.getVersionId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "产品版本不存在：" + requirement.getVersionId().value()));
        if (!version.getProjectId().equals(requirement.getProjectId())) {
            throw new com.pydlove.regent.shared.domain.DomainException(
                    "需求与产品版本不属于同一个项目");
        }
        if (version.getStatus() == ProductVersionStatus.CLOSED) {
            throw new com.pydlove.regent.shared.domain.DomainException(
                    "已关闭的产品版本不能继续评审需求");
        }
        return version;
    }

    private void requireProductReviewer(
            com.pydlove.regent.project.domain.model.ProjectId projectId,
            UserId ownerId) {
        requireProductRole(projectId, ownerId, "需求评审角色校验");
    }

    private void requireProductPlanner(
            com.pydlove.regent.project.domain.model.ProjectId projectId,
            UserId ownerId) {
        requireProductRole(projectId, ownerId, "产品版本规划角色校验");
    }

    private void requireProductRole(
            com.pydlove.regent.project.domain.model.ProjectId projectId,
            UserId ownerId,
            String feature) {
        if (memberAccessService == null) {
            throw new com.pydlove.regent.shared.domain.DomainException(
                    "当前环境未启用" + feature);
        }
        memberAccessService.requireRoleOrOwner(projectId, ownerId, ProjectMemberRole.PRODUCT);
    }

    private boolean isProjectOwner(Requirement requirement, UserId ownerId) {
        return memberAccessService != null
                && memberAccessService.roles(requirement.getProjectId(), ownerId)
                .contains(ProjectMemberRole.OWNER);
    }

    private void requireProjectOwner(
            com.pydlove.regent.project.domain.model.ProjectId projectId,
            UserId ownerId) {
        if (memberAccessService == null) {
            throw new com.pydlove.regent.shared.domain.DomainException(
                    "当前环境未启用需求删除权限校验");
        }
        memberAccessService.requireRole(projectId, ownerId, ProjectMemberRole.OWNER);
    }

    private void createFormalDelivery(Requirement requirement, UserId ownerId) {
        if (deliveryRepository == null || initiativeRepository == null) {
            throw new com.pydlove.regent.shared.domain.DomainException(
                    "当前环境未启用研发交付");
        }
        if (requirement.getInitiativeId() == null) {
            throw new com.pydlove.regent.shared.domain.DomainException(
                    "正式需求必须关联已批准立项");
        }
        Initiative initiative = initiativeRepository.findById(requirement.getInitiativeId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "立项不存在：" + requirement.getInitiativeId().value()));
        if (initiative.getStatus() != InitiativeStatus.APPROVED) {
            throw new com.pydlove.regent.shared.domain.DomainException(
                    "立项尚未批准，不能生成正式需求交付");
        }
        if (deliveryRepository.findActiveByRequirement(requirement.getId()).isPresent()) {
            throw new com.pydlove.regent.shared.domain.DomainException(
                    "该正式需求已经有进行中的研发交付");
        }
        deliveryRepository.save(RequirementDelivery.create(
                initiative.getId(),
                requirement.getProjectId(),
                requirement.getId(),
                ownerId,
                DeliveryStage.DESIGN));
    }

    private Requirement load(RequirementId id, UserId ownerId) {
        return requirementRepository.findByIdOwned(id, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("需求不存在：" + id.value()));
    }

    private ProjectProfile requireProject(com.pydlove.regent.project.domain.model.ProjectId id,
                                          UserId ownerId) {
        return projectProfileRepository.findById(id, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + id.value()));
    }

    private InitiativeId requireInitiative(InitiativeId initiativeId,
                                           com.pydlove.regent.project.domain.model.ProjectId projectId) {
        if (initiativeId == null) return null;
        if (initiativeRepository == null) {
            throw new com.pydlove.regent.shared.domain.DomainException("当前环境未启用立项关联");
        }
        Initiative initiative = initiativeRepository.findById(initiativeId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "立项不存在：" + initiativeId.value()));
        if (!initiative.getProjectId().equals(projectId)) {
            throw new com.pydlove.regent.shared.domain.DomainException("需求与立项不属于同一个项目");
        }
        if (initiative.getStatus() != InitiativeStatus.APPROVED) {
            throw new com.pydlove.regent.shared.domain.DomainException("立项尚未批准，不能录入需求");
        }
        return initiativeId;
    }

    private static String prompt(RequirementTaskDraft draft) {
        String scope = draft.scope().isEmpty() ? "未单独限制" : String.join("；", draft.scope());
        String outOfScope = draft.outOfScope().isEmpty()
                ? "无额外非目标"
                : String.join("；", draft.outOfScope());
        String acceptance = String.join("；", draft.acceptanceCriteria());
        return """
                %s

                工作范围：%s
                非目标：%s
                验收标准：%s

                完成后说明实际改动、验证方式和仍未解决的问题。
                """.formatted(draft.objective(), scope, outOfScope, acceptance).trim();
    }
}
