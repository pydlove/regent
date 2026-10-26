package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.CodeStandard;
import com.pydlove.regent.execution.domain.model.CodeStandardCopyStrategy;
import com.pydlove.regent.execution.domain.model.CodeStandardId;
import com.pydlove.regent.execution.domain.model.CodeStandardScope;
import com.pydlove.regent.execution.domain.model.CodeStandardVersion;
import com.pydlove.regent.execution.domain.model.CodeStandardVersionId;
import com.pydlove.regent.execution.domain.model.LibraryDocument;
import com.pydlove.regent.execution.domain.model.LibraryType;
import com.pydlove.regent.execution.domain.repository.CodeStandardRepository;
import com.pydlove.regent.execution.domain.repository.CodeStandardVersionRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectSummaryStatus;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeStandardServiceTest {

    private static final UserId OWNER = UserId.of(1L);
    private static final ProjectId SOURCE = ProjectId.of(1L);
    private static final ProjectId TARGET = ProjectId.of(2L);

    private final ExecutionFakes.Projects projects = new ExecutionFakes.Projects();
    private final ExecutionFakes.Documents documents = new ExecutionFakes.Documents();
    private final ExecutionFakes.AttachmentFiles files = new ExecutionFakes.AttachmentFiles();
    private final Standards standards = new Standards();
    private final Versions versions = new Versions();
    private CodeStandardService service;

    @BeforeEach
    void setUp() {
        projects.put(profile(SOURCE, "source"));
        projects.put(profile(TARGET, "target"));
        service = new CodeStandardService(
                projects, standards, versions, documents, files,
                new ExecutionFakes.Transactions(), 1024 * 1024);
    }

    @Test
    @DisplayName("上传规范时写入开发规范分类和首版文件")
    void createsDevelopmentRuleDocument() {
        var view = service.create(OWNER, command(SOURCE, "Java 规范"), markdown("# 规范\n使用 Java 17"));

        assertEquals(1, view.currentVersion().versionNo());
        assertEquals(CodeStandardScope.BACKEND, view.standard().scope());
        assertEquals(1, documents.allLibraryDocuments().size());
        assertEquals(LibraryType.DEVELOPMENT_RULE,
                documents.allLibraryDocuments().get(0).libraryType());
    }

    @Test
    @DisplayName("跨项目复制创建独立规范，相同内容再次复制时跳过")
    void copiesIndependentStandardAndSkipsDuplicateContent() {
        var source = service.create(OWNER, command(SOURCE, "Java 规范"),
                markdown("# 规范\n使用 Java 17"));

        var first = service.copy(OWNER, SOURCE, TARGET,
                List.of(source.standard().id()), CodeStandardCopyStrategy.SKIP);
        var second = service.copy(OWNER, SOURCE, TARGET,
                List.of(source.standard().id()), CodeStandardCopyStrategy.SKIP);

        assertEquals(1, first.copied().size());
        assertTrue(second.copied().isEmpty());
        assertEquals(1, second.skipped().size());
        var target = service.list(OWNER, TARGET);
        assertEquals(1, target.size());
        assertNotEquals(source.standard().id(), target.get(0).standard().id());
        assertEquals(SOURCE, target.get(0).standard().sourceProjectId());
        assertEquals(2, documents.allLibraryDocuments().size());
    }

    private static SaveCodeStandardCommand command(ProjectId projectId, String name) {
        return new SaveCodeStandardCommand(
                projectId, name, "后端任务默认遵守",
                CodeStandardScope.BACKEND, true, true, 10);
    }

    private static UploadedDocument markdown(String content) {
        return new UploadedDocument(
                "java.md", "text/markdown", content.getBytes(StandardCharsets.UTF_8));
    }

    private static ProjectProfile profile(ProjectId id, String code) {
        return new ProjectProfile(
                id, OWNER, code, code, "/tmp/" + code,
                DecisionLevel.L1, true, ProjectSummaryStatus.NOT_GENERATED,
                null, null, null);
    }

    static final class Standards implements CodeStandardRepository {
        private final Map<Long, CodeStandard> store = new LinkedHashMap<>();
        private long sequence;

        @Override
        public CodeStandard save(CodeStandard standard) {
            CodeStandard saved = standard.id() == null
                    ? standard.withIdentity(CodeStandardId.of(++sequence))
                    : standard;
            store.put(saved.id().value(), saved);
            return saved;
        }

        @Override
        public Optional<CodeStandard> findById(CodeStandardId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public Optional<CodeStandard> findByIdOwned(CodeStandardId id, UserId ownerId) {
            return findById(id);
        }

        @Override
        public List<CodeStandard> findByProject(ProjectId projectId) {
            return store.values().stream()
                    .filter(standard -> standard.projectId().equals(projectId))
                    .toList();
        }

        @Override
        public Optional<CodeStandard> findByProjectAndName(ProjectId projectId, String name) {
            return findByProject(projectId).stream()
                    .filter(standard -> standard.name().equals(name))
                    .findFirst();
        }
    }

    static final class Versions implements CodeStandardVersionRepository {
        private final Map<Long, CodeStandardVersion> store = new LinkedHashMap<>();
        private long sequence;

        @Override
        public CodeStandardVersion save(CodeStandardVersion version) {
            CodeStandardVersion saved = version.id() == null
                    ? version.withIdentity(CodeStandardVersionId.of(++sequence))
                    : version;
            store.put(saved.id().value(), saved);
            return saved;
        }

        @Override
        public Optional<CodeStandardVersion> findById(CodeStandardVersionId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public Optional<CodeStandardVersion> findByIdOwned(CodeStandardVersionId id, UserId ownerId) {
            return findById(id);
        }

        @Override
        public Optional<CodeStandardVersion> findCurrent(CodeStandardId standardId) {
            return store.values().stream()
                    .filter(version -> version.standardId().equals(standardId))
                    .max(java.util.Comparator.comparingInt(CodeStandardVersion::versionNo));
        }

        @Override
        public List<CodeStandardVersion> findByStandard(CodeStandardId standardId) {
            return store.values().stream()
                    .filter(version -> version.standardId().equals(standardId))
                    .sorted(java.util.Comparator.comparingInt(CodeStandardVersion::versionNo).reversed())
                    .toList();
        }

        @Override
        public int nextVersionNo(CodeStandardId standardId) {
            return findCurrent(standardId).map(version -> version.versionNo() + 1).orElse(1);
        }
    }
}
