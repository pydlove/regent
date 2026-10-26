package com.pydlove.regent.delivery.application.command;

import com.pydlove.regent.delivery.domain.model.DeliveryGate;
import com.pydlove.regent.delivery.domain.model.DeliveryStage;
import com.pydlove.regent.delivery.domain.model.GateScope;
import com.pydlove.regent.delivery.domain.model.Initiative;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.InitiativeStatus;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.delivery.domain.model.RequirementDelivery;
import com.pydlove.regent.delivery.domain.model.RequirementDeliveryId;
import com.pydlove.regent.delivery.domain.repository.DeliveryGateRepository;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDeliveryRepository;
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
import com.pydlove.regent.requirement.domain.model.RequirementTrashItem;
import com.pydlove.regent.requirement.domain.repository.RequirementRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.shared.kernel.DecisionLevel;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementDeliveryCommandServiceTest {

    private static final UserId OWNER = UserId.of(1L);
    private static final ProjectId PROJECT = ProjectId.of(7L);

    @Test
    @DisplayName("旧立项入口不能绕过需求池评审")
    void rejectsLegacyRequirementEntry() {
        Fixture fixture = new Fixture(approvedInitiative());

        DomainException exception = assertThrows(
                DomainException.class,
                () -> fixture.service.createWithRequirement(
                        InitiativeId.of(1L),
                        new CreateInitiativeRequirementCommand(
                                "组织数据隔离",
                                "不同账号只能看到自己的数据",
                                "A 账号看不到 B 账号录入的数据",
                                "历史数据按归属账号回填"),
                        OWNER));

        assertTrue(exception.getMessage().contains("需求池"));
        assertEquals(0, fixture.requirements.store.size());
    }

    @Test
    @DisplayName("未批准立项不能录入需求")
    void rejectsUnapprovedInitiative() {
        Initiative initiative = Initiative.create(
                PROJECT, OWNER, "草稿立项", "验证门禁", null, null, null);
        Fixture fixture = new Fixture(initiative);

        assertThrows(DomainException.class, () -> fixture.service.createWithRequirement(
                initiative.getId(),
                new CreateInitiativeRequirementCommand(
                        "需求", "描述", null, null),
                OWNER));
        assertEquals(0, fixture.requirements.store.size());
    }

    private static Initiative approvedInitiative() {
        Initiative initiative = Initiative.create(
                PROJECT, OWNER, "研发流程", "贯通立项到评审", null, null, null);
        initiative.submit();
        initiative.approve();
        return initiative;
    }

    private static final class Fixture {
        private final Requirements requirements = new Requirements();
        private final Deliveries deliveries = new Deliveries();
        private final RequirementDeliveryCommandService service;

        private Fixture(Initiative initiative) {
            Initiatives initiatives = new Initiatives();
            initiatives.save(initiative);
            service = new RequirementDeliveryCommandService(
                    deliveries,
                    initiatives,
                    requirements,
                    new Gates(),
                    new ProjectMemberAccessService(new Members()),
                    new Projects(),
                    new DirectTransactions());
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

    private static final class Requirements implements RequirementRepository {
        private final Map<Long, Requirement> store = new LinkedHashMap<>();
        private long sequence;
        private long requirementSequence;

        @Override
        public long nextRequirementNo() {
            return ++requirementSequence;
        }

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
        public boolean existsByVersion(ProductVersionId versionId) {
            return false;
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
        public List<RequirementDelivery> findByIds(
                Collection<RequirementDeliveryId> ids) {
            return ids.stream()
                    .map(this::findById)
                    .flatMap(Optional::stream)
                    .toList();
        }

        @Override
        public Optional<RequirementDelivery> findActiveByRequirement(
                RequirementId requirementId) {
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

    private static final class Gates implements DeliveryGateRepository {
        @Override
        public DeliveryGate save(DeliveryGate gate) {
            return gate;
        }

        @Override
        public List<DeliveryGate> findByScope(GateScope scope, long scopeId) {
            return List.of();
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

    private static final class Members implements ProjectMemberRepository {
        @Override
        public List<ProjectMember> findByProject(ProjectId projectId) {
            return List.of();
        }

        @Override
        public List<ProjectMemberRole> findRoles(ProjectId projectId, UserId userId) {
            return List.of();
        }

        @Override
        public boolean exists(ProjectId projectId, UserId userId, ProjectMemberRole role) {
            return false;
        }

        @Override
        public int countDistinctUsers(ProjectId projectId) {
            return 0;
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
