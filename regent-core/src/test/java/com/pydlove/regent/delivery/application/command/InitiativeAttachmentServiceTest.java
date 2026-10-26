package com.pydlove.regent.delivery.application.command;

import com.pydlove.regent.delivery.domain.model.Initiative;
import com.pydlove.regent.delivery.domain.model.InitiativeAttachment;
import com.pydlove.regent.delivery.domain.model.InitiativeAttachmentId;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.delivery.domain.repository.InitiativeAttachmentRepository;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.port.AttachmentStorage;
import com.pydlove.regent.shared.kernel.AttachmentScope;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitiativeAttachmentServiceTest {

    private static final UserId OWNER = UserId.of(1L);
    private static final ProjectId PROJECT = ProjectId.of(7L);

    @Test
    @DisplayName("审批前可以上传 PDF 会议纪要")
    void attachesMinutesDocument() {
        Fixture fixture = new Fixture();
        Initiative initiative = fixture.initiative();

        InitiativeAttachment attachment = fixture.service.attach(
                initiative.getId(),
                OWNER,
                new UploadedInitiativeAttachment(
                        "立项会议纪要.pdf",
                        "application/pdf",
                        "%PDF-1.4\n立项会议纪要".getBytes()));

        assertEquals("application/pdf", attachment.contentType());
        assertEquals(initiative.getId(), attachment.initiativeId());
        assertTrue(attachment.storagePath().startsWith("stored/"));
    }

    @Test
    @DisplayName("立项批准后不能继续补审批附件")
    void rejectsUploadAfterApproval() {
        Fixture fixture = new Fixture();
        Initiative initiative = fixture.initiative();
        initiative.submit();
        initiative.approve();
        fixture.initiatives.save(initiative);

        assertThrows(DomainException.class, () -> fixture.service.attach(
                initiative.getId(),
                OWNER,
                new UploadedInitiativeAttachment(
                        "补充纪要.txt", "text/plain", "补充".getBytes())));
    }

    @Test
    @DisplayName("不支持的会议纪要附件类型会被拒绝")
    void rejectsUnsupportedDocument() {
        Fixture fixture = new Fixture();
        Initiative initiative = fixture.initiative();

        assertThrows(DomainException.class, () -> fixture.service.attach(
                initiative.getId(),
                OWNER,
                new UploadedInitiativeAttachment(
                        "minutes.html", "text/html", "<script>alert(1)</script>".getBytes())));
    }

    private static final class Fixture {
        private final Initiatives initiatives = new Initiatives();
        private final InitiativeAttachmentService service;

        private Fixture() {
            Initiative initiative = Initiative.create(
                    PROJECT, OWNER, "立项审批", "验证会议纪要", null, null, null);
            initiatives.save(initiative);
            service = new InitiativeAttachmentService(
                    initiatives,
                    new Attachments(),
                    new Projects(),
                    new Storage(),
                    1024 * 1024);
        }

        private Initiative initiative() {
            return initiatives.store.values().iterator().next().initiative;
        }
    }

    private static final class Initiatives implements InitiativeRepository {
        private final Map<Long, Row> store = new LinkedHashMap<>();
        private long sequence;

        @Override
        public Optional<Initiative> findById(InitiativeId id) {
            return Optional.ofNullable(store.get(id.value())).map(Row::initiative);
        }

        @Override
        public List<Initiative> findByProject(ProjectId projectId) {
            return store.values().stream()
                    .map(Row::initiative)
                    .filter(initiative -> initiative.getProjectId().equals(projectId))
                    .toList();
        }

        @Override
        public Optional<Initiative> findByVersion(ProductVersionId versionId) {
            return store.values().stream()
                    .map(Row::initiative)
                    .filter(initiative -> versionId.equals(initiative.getVersionId()))
                    .findFirst();
        }

        @Override
        public void save(Initiative initiative) {
            if (initiative.getId() == null) {
                initiative.assignIdentity(InitiativeId.of(++sequence));
            }
            store.put(initiative.getId().value(), new Row(initiative));
        }

        private record Row(Initiative initiative) {
        }
    }

    private static final class Attachments implements InitiativeAttachmentRepository {
        private final Map<Long, InitiativeAttachment> store = new LinkedHashMap<>();
        private long sequence;

        @Override
        public InitiativeAttachment save(InitiativeAttachment attachment) {
            InitiativeAttachment saved = attachment.withIdentity(
                    InitiativeAttachmentId.of(++sequence));
            store.put(saved.id().value(), saved);
            return saved;
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

    private static final class Storage implements AttachmentStorage {
        private long sequence;

        @Override
        public String store(AttachmentScope scope, long ownerId, String extension, byte[] content) {
            return "stored/" + scope.name() + "-" + (++sequence) + extension;
        }

        @Override
        public byte[] read(String storagePath) {
            return new byte[0];
        }

        @Override
        public void delete(String storagePath) {
        }
    }
}
