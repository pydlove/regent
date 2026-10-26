package com.pydlove.regent.delivery.application.command;

import com.pydlove.regent.delivery.domain.model.DeliveryGate;
import com.pydlove.regent.delivery.domain.model.GateScope;
import com.pydlove.regent.delivery.domain.model.Initiative;
import com.pydlove.regent.delivery.domain.model.InitiativeAttachment;
import com.pydlove.regent.delivery.domain.model.InitiativeAttachmentId;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.InitiativeStatus;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.delivery.domain.repository.DeliveryGateRepository;
import com.pydlove.regent.delivery.domain.repository.InitiativeAttachmentRepository;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.query.ProjectMemberAccessService;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectMember;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectMemberRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InitiativeCommandServiceTest {

    private static final UserId OWNER = UserId.of(1L);
    private static final ProjectId PROJECT = ProjectId.of(7L);

    @Test
    @DisplayName("发起版本立项时必须先提供会议纪要")
    void requiresMeetingEvidenceBeforeSubmission() {
        Fixture fixture = new Fixture();
        Initiative initiative = fixture.versionDraft(null);

        assertThrows(DomainException.class,
                () -> fixture.service.submit(initiative.getId(), OWNER));
        assertEquals(InitiativeStatus.DRAFT, initiative.getStatus());
    }

    @Test
    @DisplayName("审批只记录意见，不再要求补充会议纪要")
    void approvalDoesNotRequireNewMeetingEvidence() {
        Fixture fixture = new Fixture();
        Initiative initiative = fixture.versionDraft("线下评审已通过");
        fixture.service.submit(initiative.getId(), OWNER);

        fixture.service.approve(initiative.getId(), OWNER, "同意");

        assertEquals(InitiativeStatus.APPROVED, initiative.getStatus());
        assertEquals("线下评审已通过", initiative.getMeetingMinutes());
    }

    @Test
    @DisplayName("发起时只上传会议纪要附件即可提交并审批")
    void submitsWithAttachmentAndApproves() {
        Fixture fixture = new Fixture();
        Initiative initiative = fixture.versionDraft(null);
        fixture.attachments.add(new InitiativeAttachment(
                InitiativeAttachmentId.of(1),
                initiative.getId(),
                "会议纪要.pdf",
                "application/pdf",
                128,
                "/tmp/minutes.pdf",
                OWNER,
                Instant.now()));

        fixture.service.submit(initiative.getId(), OWNER);
        fixture.service.approve(initiative.getId(), OWNER, "同意");

        assertEquals(InitiativeStatus.APPROVED, initiative.getStatus());
    }

    @Test
    @DisplayName("多人项目中项目负责人不能代替产品负责人发起版本立项")
    void rejectsOwnerWithoutProductRoleInTeamProject() {
        Fixture fixture = new Fixture(
                new Members(List.of(ProjectMemberRole.OWNER), 2));

        assertThrows(DomainException.class, () -> fixture.service.create(
                new SaveInitiativeCommand(
                        PROJECT,
                        "版本立项",
                        "验证角色边界",
                        null,
                        null,
                        null),
                OWNER));
    }

    private static final class Fixture {
        private final Initiatives initiatives = new Initiatives();
        private final Attachments attachments = new Attachments();
        private final InitiativeCommandService service;

        private Fixture() {
            this(new Members());
        }

        private Fixture(ProjectMemberRepository members) {
            service = new InitiativeCommandService(
                    initiatives,
                    attachments,
                    new Gates(),
                    new ProjectMemberAccessService(members),
                    new Projects(),
                    new DirectTransactions());
        }

        private Initiative versionDraft(String meetingMinutes) {
            Initiative initiative = Initiative.create(
                    PROJECT,
                    ProductVersionId.of(99L),
                    OWNER,
                    "版本立项",
                    "验证会议纪要提交时点",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
            if (meetingMinutes != null) {
                initiative.updateMeetingMinutes(meetingMinutes);
            }
            initiatives.save(initiative);
            return initiative;
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

    private static final class Attachments implements InitiativeAttachmentRepository {
        private final Map<Long, InitiativeAttachment> store = new LinkedHashMap<>();

        private void add(InitiativeAttachment attachment) {
            store.put(attachment.id().value(), attachment);
        }

        @Override
        public InitiativeAttachment save(InitiativeAttachment attachment) {
            add(attachment);
            return attachment;
        }

        @Override
        public Optional<InitiativeAttachment> findById(InitiativeAttachmentId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public Optional<InitiativeAttachment> findByIdOwned(
                InitiativeAttachmentId id, UserId ownerId) {
            return findById(id);
        }

        @Override
        public List<InitiativeAttachment> findByInitiative(InitiativeId initiativeId) {
            return store.values().stream()
                    .filter(attachment -> attachment.initiativeId().equals(initiativeId))
                    .toList();
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

    private static final class Members implements ProjectMemberRepository {
        private final List<ProjectMemberRole> roles;
        private final int count;

        private Members() {
            this(List.of(ProjectMemberRole.OWNER), 1);
        }

        private Members(List<ProjectMemberRole> roles, int count) {
            this.roles = roles;
            this.count = count;
        }

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
            return true;
        }

        @Override
        public int countDistinctUsers(ProjectId projectId) {
            return count;
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

    private static final class Projects implements ProjectProfileRepository {
        private final ProjectProfile project = new ProjectProfile(
                PROJECT, "regent", "Regent", "/tmp/regent", DecisionLevel.L1, true);

        @Override
        public Optional<ProjectProfile> findById(ProjectId id) {
            return id.equals(PROJECT) ? Optional.of(project) : Optional.empty();
        }

        @Override
        public Optional<ProjectProfile> findByCode(String code) {
            return "regent".equals(code) ? Optional.of(project) : Optional.empty();
        }

        @Override
        public List<ProjectProfile> findAll() {
            return List.of(project);
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
