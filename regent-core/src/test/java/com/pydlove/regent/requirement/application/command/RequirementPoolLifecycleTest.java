package com.pydlove.regent.requirement.application.command;

import com.pydlove.regent.delivery.domain.model.DeliveryStage;
import com.pydlove.regent.delivery.domain.model.Initiative;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.ProductVersion;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.delivery.domain.model.ProductVersionStatus;
import com.pydlove.regent.delivery.domain.model.RequirementDelivery;
import com.pydlove.regent.delivery.domain.model.RequirementDeliveryId;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.delivery.domain.repository.ProductVersionRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDeliveryRepository;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.query.ProjectMemberAccessService;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectMember;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectMemberRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.requirement.domain.model.Requirement;
import com.pydlove.regent.requirement.domain.model.RequirementId;
import com.pydlove.regent.requirement.domain.model.RequirementIdeaDraft;
import com.pydlove.regent.requirement.domain.model.RequirementIdeaRequest;
import com.pydlove.regent.requirement.domain.model.RequirementPoolStatus;
import com.pydlove.regent.requirement.domain.model.RequirementSource;
import com.pydlove.regent.requirement.domain.model.RequirementTrashItem;
import com.pydlove.regent.requirement.domain.port.RequirementIdeaDesigner;
import com.pydlove.regent.requirement.domain.repository.RequirementRepository;
import com.pydlove.regent.requirement.domain.repository.TaskDependencyRepository;
import com.pydlove.regent.shared.domain.DomainException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequirementPoolLifecycleTest {

    private static final UserId OWNER = UserId.of(1L);
    private static final ProjectId PROJECT = ProjectId.of(7L);
    private static final ProjectId OTHER_PROJECT = ProjectId.of(8L);

    @Test
    @DisplayName("版本确认后仍可完成已纳入需求的评审并自动创建设计交付")
    void confirmsVersionThenCompletesExistingReview() {
        Fixture fixture = new Fixture();
        ProductVersion version = fixture.productVersions.create(PROJECT, "v1.0.0");
        Initiative initiative = fixture.approvedInitiative(version.getId());
        Requirement requirement = fixture.createRequirement(initiative.getId());

        fixture.service.assignVersion(requirement.getId(), version.getId(), OWNER);
        fixture.service.submitPoolReview(requirement.getId(), OWNER);
        fixture.productVersions.confirm(version.getId());
        Requirement approved = fixture.service.approvePoolReview(
                requirement.getId(), OWNER, "评审通过");

        assertEquals(RequirementPoolStatus.FORMAL, approved.getPoolStatus());
        RequirementDelivery delivery = fixture.deliveries.findActiveByRequirement(requirement.getId())
                .orElseThrow();
        assertEquals(DeliveryStage.DESIGN, delivery.getStage());
        assertEquals(initiative.getId(), delivery.getInitiativeId());
    }

    @Test
    @DisplayName("已关闭的产品版本不能继续提交或评审需求")
    void rejectsReviewForClosedVersion() {
        Fixture fixture = new Fixture();
        ProductVersion version = fixture.productVersions.create(PROJECT, "v1.0.0");
        Initiative initiative = fixture.approvedInitiative(version.getId());
        Requirement requirement = fixture.createRequirement(initiative.getId());
        fixture.service.assignVersion(requirement.getId(), version.getId(), OWNER);
        fixture.productVersions.close(version.getId());

        DomainException exception = assertThrows(
                DomainException.class,
                () -> fixture.service.submitPoolReview(requirement.getId(), OWNER));

        assertEquals("已关闭的产品版本不能继续评审需求", exception.getMessage());
    }

    @Test
    @DisplayName("需求不能纳入其他项目的产品版本")
    void rejectsCrossProjectVersion() {
        Fixture fixture = new Fixture();
        ProductVersion localVersion = fixture.productVersions.create(PROJECT, "v1.0.0");
        Initiative initiative = fixture.approvedInitiative(localVersion.getId());
        ProductVersion version = fixture.productVersions.create(OTHER_PROJECT, "v9.0.0");
        Requirement requirement = fixture.createRequirement(initiative.getId());

        DomainException exception = assertThrows(
                DomainException.class,
                () -> fixture.service.assignVersion(requirement.getId(), version.getId(), OWNER));

        assertEquals("需求与产品版本不属于同一个项目", exception.getMessage());
    }

    @Test
    @DisplayName("无项目角色不能通过需求评审")
    void rejectsUnauthorizedReviewer() {
        Fixture fixture = new Fixture();
        ProductVersion version = fixture.productVersions.create(PROJECT, "v1.0.0");
        Initiative initiative = fixture.approvedInitiative(version.getId());
        Requirement requirement = fixture.createRequirement(initiative.getId());
        fixture.service.assignVersion(requirement.getId(), version.getId(), OWNER);
        fixture.service.submitPoolReview(requirement.getId(), OWNER);

        assertThrows(DomainException.class,
                () -> fixture.service.approvePoolReview(
                        requirement.getId(), UserId.of(99L), "越权通过"));
    }

    @Test
    @DisplayName("需求池草稿删除进入回收站后可以恢复")
    void movesRequirementToTrashAndRestores() {
        Fixture fixture = new Fixture();
        ProductVersion version = fixture.productVersions.create(PROJECT, "v1.0.0");
        Initiative initiative = fixture.approvedInitiative(version.getId());
        Requirement requirement = fixture.createRequirement(initiative.getId());

        fixture.service.moveToTrash(requirement.getId(), OWNER);

        assertEquals(1, fixture.requirements.findDeletedByProject(PROJECT, OWNER).size());
        assertThrows(DomainException.class,
                () -> fixture.service.assignVersion(requirement.getId(), version.getId(), OWNER));

        fixture.service.restoreFromTrash(requirement.getId(), OWNER);

        assertEquals(0, fixture.requirements.findDeletedByProject(PROJECT, OWNER).size());
        assertEquals(requirement.getId(), fixture.requirements.findById(requirement.getId())
                .orElseThrow().getId());
    }

    @Test
    @DisplayName("正式需求不能删除到回收站")
    void rejectsDeletingFormalRequirement() {
        Fixture fixture = new Fixture();
        ProductVersion version = fixture.productVersions.create(PROJECT, "v1.0.0");
        Initiative initiative = fixture.approvedInitiative(version.getId());
        Requirement requirement = fixture.createRequirement(initiative.getId());
        fixture.service.assignVersion(requirement.getId(), version.getId(), OWNER);
        fixture.service.submitPoolReview(requirement.getId(), OWNER);
        fixture.service.approvePoolReview(requirement.getId(), OWNER, "通过");

        DomainException exception = assertThrows(DomainException.class,
                () -> fixture.service.moveToTrash(requirement.getId(), OWNER));

        assertEquals("评审中或已正式化的需求不能删除", exception.getMessage());
    }

    @Test
    @DisplayName("AI 想法一次拆成多条需求并全部进入需求池")
    void generatesMultipleRequirementsFromIdea() {
        Fixture fixture = new Fixture();
        ProductVersion version = fixture.productVersions.create(PROJECT, "v1.0.0");
        Initiative initiative = fixture.approvedInitiative(version.getId());
        RequirementIdeaDesigner designer = request -> List.of(
                new RequirementIdeaDraft(
                        "组织数据隔离",
                        "不同组织的数据必须隔离。",
                        "A 组织不能查询 B 组织数据",
                        List.of("历史数据归属"),
                        List.of("沿用现有组织模型")),
                new RequirementIdeaDraft(
                        "组织角色分配",
                        "管理员可以为成员分配角色。",
                        "角色变更后立即生效",
                        List.of(),
                        List.of()));
        RequirementIdeaService ideaService = new RequirementIdeaService(
                new Projects(), designer, fixture.service, new DirectTransactions());

        List<Requirement> generated = ideaService.generate(
                PROJECT, initiative.getId(), "希望组织隔离并支持成员角色", OWNER);

        assertEquals(2, generated.size());
        assertEquals(RequirementSource.AI, generated.get(0).getSource());
        assertEquals(2, fixture.requirements.findByProject(PROJECT).size());
        assertEquals(initiative.getId(), generated.get(1).getInitiativeId());
    }

    private static final class Fixture {
        private final Requirements requirements = new Requirements();
        private final Initiatives initiatives = new Initiatives();
        private final ProductVersions productVersions = new ProductVersions();
        private final Deliveries deliveries = new Deliveries();
        private final RequirementCommandService service = new RequirementCommandService(
                requirements,
                new ExecutionFakes.Tasks(),
                new Dependencies(),
                new Projects(),
                null,
                new DirectTransactions(),
                null,
                initiatives,
                productVersions,
                deliveries,
                new ProjectMemberAccessService(new Members()));

        private Initiative approvedInitiative(ProductVersionId versionId) {
            Initiative initiative = Initiative.create(
                    PROJECT, versionId, OWNER, "研发流程", "贯通立项到评审",
                    null, null, null, null, null, null);
            initiative.submit();
            initiative.approve("评审纪要");
            initiatives.save(initiative);
            return initiative;
        }

        private Requirement createRequirement(InitiativeId initiativeId) {
            return service.create(new SaveRequirementCommand(
                    PROJECT,
                    initiativeId,
                    "组织数据隔离",
                    "不同组织之间不能看到彼此数据",
                    "A 组织成员无法查询 B 组织项目",
                    null), OWNER);
        }
    }

    private static final class Requirements implements RequirementRepository {
        private final Map<Long, Requirement> store = new LinkedHashMap<>();
        private final Map<Long, RequirementTrashItem> trash = new LinkedHashMap<>();
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
            return trash.values().stream()
                    .filter(item -> item.requirement().getProjectId().equals(projectId))
                    .toList();
        }

        @Override
        public Optional<RequirementTrashItem> findDeletedByIdOwned(RequirementId id,
                                                                   UserId ownerId) {
            return Optional.ofNullable(trash.get(id.value()));
        }

        @Override
        public void moveToTrash(RequirementId id, UserId deletedBy, Instant deletedAt) {
            Requirement requirement = store.remove(id.value());
            if (requirement == null) {
                throw new DomainException("需求不存在：" + id.value());
            }
            trash.put(id.value(), new RequirementTrashItem(requirement, deletedAt, deletedBy));
        }

        @Override
        public void restore(RequirementId id, Instant updatedAt) {
            RequirementTrashItem item = trash.remove(id.value());
            if (item == null) {
                throw new DomainException("回收站中不存在该需求：" + id.value());
            }
            store.put(id.value(), item.requirement());
        }

        @Override
        public void save(Requirement requirement) {
            if (requirement.getId() == null) {
                requirement.assignIdentity(RequirementId.of(++sequence));
            }
            store.put(requirement.getId().value(), requirement);
        }
    }

    private static final class Initiatives implements InitiativeRepository {
        private final Map<Long, Initiative> store = new LinkedHashMap<>();
        private long sequence;

        @Override
        public Optional<Initiative> findById(InitiativeId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public List<Initiative> findByProject(ProjectId projectId) {
            return store.values().stream()
                    .filter(initiative -> initiative.getProjectId().equals(projectId))
                    .toList();
        }

        @Override
        public Optional<Initiative> findByVersion(ProductVersionId versionId) {
            return store.values().stream()
                    .filter(initiative -> versionId.equals(initiative.getVersionId()))
                    .findFirst();
        }

        @Override
        public void save(Initiative initiative) {
            if (initiative.getId() == null) {
                initiative.assignIdentity(InitiativeId.of(++sequence));
            }
            store.put(initiative.getId().value(), initiative);
        }
    }

    private static final class ProductVersions implements ProductVersionRepository {
        private final Map<Long, ProductVersion> store = new LinkedHashMap<>();
        private long sequence;

        private ProductVersion create(ProjectId projectId, String versionNo) {
            ProductVersion version = ProductVersion.create(
                    projectId, versionNo, "测试版本", null, OWNER);
            save(version);
            return version;
        }

        private void confirm(ProductVersionId id) {
            ProductVersion version = findById(id).orElseThrow();
            version.confirm();
            update(version);
        }

        private void close(ProductVersionId id) {
            ProductVersion version = findById(id).orElseThrow();
            version.close();
            update(version);
        }

        @Override
        public Optional<ProductVersion> findById(ProductVersionId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public List<ProductVersion> findByProject(ProjectId projectId) {
            return store.values().stream()
                    .filter(version -> version.getProjectId().equals(projectId))
                    .toList();
        }

        @Override
        public boolean existsByProjectAndVersionNo(ProjectId projectId, String versionNo) {
            return store.values().stream()
                    .anyMatch(version -> version.getProjectId().equals(projectId)
                            && version.getVersionNo().equals(versionNo));
        }

        @Override
        public void save(ProductVersion version) {
            if (version.getId() == null) {
                version.assignIdentity(ProductVersionId.of(++sequence));
            }
            store.put(version.getId().value(), version);
        }

        @Override
        public void update(ProductVersion version) {
            store.put(version.getId().value(), version);
        }
    }

    private static final class Deliveries implements RequirementDeliveryRepository {
        private final Map<Long, RequirementDelivery> store = new LinkedHashMap<>();
        private long sequence;

        @Override
        public Optional<RequirementDelivery> findById(RequirementDeliveryId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public List<RequirementDelivery> findByProject(ProjectId projectId) {
            return store.values().stream()
                    .filter(delivery -> delivery.getProjectId().equals(projectId))
                    .toList();
        }

        @Override
        public List<RequirementDelivery> findByIds(Collection<RequirementDeliveryId> ids) {
            return ids.stream()
                    .map(this::findById)
                    .flatMap(Optional::stream)
                    .toList();
        }

        @Override
        public Optional<RequirementDelivery> findActiveByRequirement(RequirementId requirementId) {
            return store.values().stream()
                    .filter(delivery -> delivery.getRequirementId().equals(requirementId))
                    .findFirst();
        }

        @Override
        public void save(RequirementDelivery delivery) {
            if (delivery.getId() == null) {
                delivery.assignIdentity(RequirementDeliveryId.of(++sequence));
            }
            store.put(delivery.getId().value(), delivery);
        }
    }

    private static final class Projects implements ProjectProfileRepository {
        private final Map<ProjectId, ProjectProfile> store = Map.of(
                PROJECT, new ProjectProfile(
                        PROJECT, "regent", "Regent", "/tmp/regent",
                        DecisionLevel.L1, true),
                OTHER_PROJECT, new ProjectProfile(
                        OTHER_PROJECT, "other", "Other", "/tmp/other",
                        DecisionLevel.L1, true));

        @Override
        public Optional<ProjectProfile> findById(ProjectId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<ProjectProfile> findByCode(String code) {
            return store.values().stream()
                    .filter(project -> project.code().equals(code))
                    .findFirst();
        }

        @Override
        public List<ProjectProfile> findAll() {
            return List.copyOf(store.values());
        }
    }

    private static final class Members implements ProjectMemberRepository {
        @Override
        public List<ProjectMember> findByProject(ProjectId projectId) {
            return List.of();
        }

        @Override
        public List<ProjectMemberRole> findRoles(ProjectId projectId, UserId userId) {
            return OWNER.equals(userId)
                    ? List.of(ProjectMemberRole.OWNER)
                    : List.of();
        }

        @Override
        public boolean exists(ProjectId projectId, UserId userId, ProjectMemberRole role) {
            return findRoles(projectId, userId).contains(role);
        }

        @Override
        public int countDistinctUsers(ProjectId projectId) {
            return 3;
        }

        @Override
        public ProjectMember save(ProjectMember member) {
            return member;
        }

        @Override
        public void delete(ProjectId projectId, UserId userId, ProjectMemberRole role) {
        }

        @Override
        public void ensure(ProjectId projectId, UserId userId, ProjectMemberRole role) {
        }
    }

    private static final class Dependencies implements TaskDependencyRepository {
        @Override
        public void replace(TaskId taskId, Collection<TaskId> dependsOnTaskIds) {
        }

        @Override
        public List<TaskId> findDependencies(TaskId taskId) {
            return List.of();
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
