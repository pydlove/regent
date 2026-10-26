package com.pydlove.regent.delivery.application.command;

import com.pydlove.regent.delivery.domain.model.Initiative;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.ProductVersion;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.delivery.domain.repository.ProductVersionRepository;
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
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductVersionCommandServiceTest {

    private static final ProjectId PROJECT = ProjectId.of(7L);
    private static final UserId OWNER = UserId.of(1L);
    private static final UserId PRODUCT_OWNER = UserId.of(2L);

    @Test
    @DisplayName("项目负责人可以删除未使用的产品版本")
    void ownerDeletesUnusedVersion() {
        Fixture fixture = new Fixture(List.of(ProjectMemberRole.OWNER));
        ProductVersion version = fixture.createVersion();

        fixture.service().delete(version.getId(), OWNER);

        assertTrue(fixture.versions.findById(version.getId()).isEmpty());
    }

    @Test
    @DisplayName("非项目负责人不能删除产品版本")
    void nonOwnerCannotDeleteVersion() {
        Fixture fixture = new Fixture(List.of(ProjectMemberRole.PRODUCT));
        ProductVersion version = fixture.createVersion();

        assertThrows(DomainException.class,
                () -> fixture.service().delete(version.getId(), PRODUCT_OWNER));
        assertTrue(fixture.versions.findById(version.getId()).isPresent());
    }

    @Test
    @DisplayName("已有需求的版本不能删除，仅立项的版本关闭后可以删除")
    void linkedVersionCannotBeDeleted() {
        Fixture fixture = new Fixture(List.of(ProjectMemberRole.OWNER));
        ProductVersion version = fixture.createVersion();
        fixture.requirements.linked = true;

        assertThrows(DomainException.class,
                () -> fixture.service().delete(version.getId(), OWNER));
        assertTrue(fixture.versions.findById(version.getId()).isPresent());

        fixture.requirements.linked = false;
        fixture.initiatives.linkedInitiative = Initiative.create(
                PROJECT,
                version.getId(),
                OWNER,
                "版本立项",
                "验证版本删除约束",
                null,
                null,
                null,
                null,
                null,
                null);

        DomainException exception = assertThrows(DomainException.class,
                () -> fixture.service().delete(version.getId(), OWNER));
        assertEquals("产品版本已有立项，请先关闭版本后再删除", exception.getMessage());
        assertTrue(fixture.versions.findById(version.getId()).isPresent());

        version.close();
        fixture.versions.update(version);
        fixture.service().delete(version.getId(), OWNER);

        assertTrue(fixture.versions.findById(version.getId()).isEmpty());
        assertTrue(fixture.initiatives.findByVersion(version.getId()).isEmpty());
    }

    private static final class Fixture {
        private final Members members;
        private final Versions versions = new Versions();
        private final Initiatives initiatives = new Initiatives();
        private final Requirements requirements = new Requirements();

        private Fixture(List<ProjectMemberRole> roles) {
            members = new Members(roles);
        }

        private ProductVersionCommandService service() {
            return new ProductVersionCommandService(
                    versions,
                    new Projects(),
                    new ProjectMemberAccessService(members),
                    initiatives,
                    requirements);
        }

        private ProductVersion createVersion() {
            ProductVersion version = ProductVersion.create(
                    PROJECT, "AT_V1.0.0_R100", "自动化测试平台", null, OWNER);
            versions.save(version);
            return version;
        }
    }

    private static final class Versions implements ProductVersionRepository {
        private final Map<Long, ProductVersion> store = new LinkedHashMap<>();
        private long sequence;

        @Override
        public Optional<ProductVersion> findById(ProductVersionId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public List<ProductVersion> findByProject(ProjectId projectId) {
            return List.copyOf(store.values());
        }

        @Override
        public boolean existsByProjectAndVersionNo(ProjectId projectId, String versionNo) {
            return store.values().stream()
                    .anyMatch(version -> version.getVersionNo().equals(versionNo));
        }

        @Override
        public void save(ProductVersion version) {
            version.assignIdentity(ProductVersionId.of(++sequence));
            store.put(version.getId().value(), version);
        }

        @Override
        public void update(ProductVersion version) {
            store.put(version.getId().value(), version);
        }

        @Override
        public void delete(ProductVersionId id) {
            store.remove(id.value());
        }
    }

    private static final class Initiatives implements InitiativeRepository {
        private Initiative linkedInitiative;

        @Override
        public Optional<Initiative> findById(InitiativeId id) {
            return Optional.ofNullable(linkedInitiative);
        }

        @Override
        public List<Initiative> findByProject(ProjectId projectId) {
            return linkedInitiative == null ? List.of() : List.of(linkedInitiative);
        }

        @Override
        public Optional<Initiative> findByVersion(ProductVersionId versionId) {
            return Optional.ofNullable(linkedInitiative);
        }

        @Override
        public void save(Initiative initiative) {
            linkedInitiative = initiative;
        }

        @Override
        public void deleteByVersion(ProductVersionId versionId) {
            if (linkedInitiative != null
                    && versionId.equals(linkedInitiative.getVersionId())) {
                linkedInitiative = null;
            }
        }
    }

    private static final class Requirements implements RequirementRepository {
        private boolean linked;

        @Override
        public long nextRequirementNo() {
            return 0;
        }

        @Override
        public Optional<Requirement> findById(RequirementId id) {
            return Optional.empty();
        }

        @Override
        public List<Requirement> findByProject(ProjectId projectId) {
            return List.of();
        }

        @Override
        public List<Requirement> findAll(UserId ownerId) {
            return List.of();
        }

        @Override
        public List<RequirementTrashItem> findDeletedByProject(ProjectId projectId,
                                                                UserId ownerId) {
            return List.of();
        }

        @Override
        public Optional<RequirementTrashItem> findDeletedByIdOwned(RequirementId id,
                                                                   UserId ownerId) {
            return Optional.empty();
        }

        @Override
        public boolean existsByVersion(ProductVersionId versionId) {
            return linked;
        }

        @Override
        public void moveToTrash(RequirementId id, UserId deletedBy, Instant deletedAt) {
        }

        @Override
        public void restore(RequirementId id, Instant updatedAt) {
        }

        @Override
        public void save(Requirement requirement) {
        }
    }

    private static final class Projects implements ProjectProfileRepository {
        private final ProjectProfile profile = new ProjectProfile(
                PROJECT, "regent", "Regent", "/tmp/regent", DecisionLevel.L1, true);

        @Override
        public Optional<ProjectProfile> findById(ProjectId id) {
            return Optional.of(profile);
        }

        @Override
        public Optional<ProjectProfile> findByCode(String code) {
            return Optional.of(profile);
        }

        @Override
        public List<ProjectProfile> findAll() {
            return List.of(profile);
        }
    }

    private record Members(List<ProjectMemberRole> roles) implements ProjectMemberRepository {

        @Override
        public List<ProjectMember> findByProject(ProjectId projectId) {
            return List.of();
        }

        @Override
        public List<ProjectMemberRole> findRoles(ProjectId projectId, UserId userId) {
            return roles;
        }

        @Override
        public boolean exists(ProjectId projectId, UserId userId, ProjectMemberRole role) {
            return roles.contains(role);
        }

        @Override
        public int countDistinctUsers(ProjectId projectId) {
            return roles.size();
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
}
