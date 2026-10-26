package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.CodeStandard;
import com.pydlove.regent.execution.domain.model.CodeStandardScope;
import com.pydlove.regent.execution.domain.model.CodeStandardVersion;
import com.pydlove.regent.execution.domain.model.LibraryDocument;
import com.pydlove.regent.execution.domain.model.LibraryDocumentId;
import com.pydlove.regent.execution.domain.model.LibraryType;
import com.pydlove.regent.execution.domain.model.TaskCodeStandard;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskCodeStandardServiceTest {

    private static final UserId OWNER = UserId.of(1L);
    private static final ProjectId PROJECT = ProjectId.of(1L);
    private static final TaskId TASK = TaskId.of(1L);

    private final ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
    private final ExecutionFakes.AttachmentFiles files = new ExecutionFakes.AttachmentFiles();
    private final CodeStandardServiceTest.Standards standards = new CodeStandardServiceTest.Standards();
    private final CodeStandardServiceTest.Versions versions = new CodeStandardServiceTest.Versions();
    private final TaskStandards taskStandards = new TaskStandards();
    private TaskCodeStandardService service;

    @BeforeEach
    void setUp() {
        tasks.add(ExecutionFakes.task(1L, PROJECT, TaskStatus.PENDING));
        service = new TaskCodeStandardService(
                tasks, standards, versions, taskStandards, files, 1024 * 1024);
    }

    @Test
    @DisplayName("默认规范在任务上固定版本并注入执行正文")
    void snapshotsDefaultStandardAndBuildsPrompt() {
        var standard = standard("Java 规范", true);
        var version = version(standard, 1, "# 必须使用 Java 17");

        service.snapshot(TASK, PROJECT, null);
        var context = service.context(TASK);

        assertTrue(context.promptSection().contains("《Java 规范》 v1"));
        assertTrue(context.promptSection().contains("# 必须使用 Java 17"));
        assertTrue(context.auditPayload().contains(version.contentHash()));
        assertEquals(1, taskStandards.findByTask(TASK).size());
    }

    @Test
    @DisplayName("规范后续新增版本不会改变已有任务快照")
    void existingSnapshotKeepsOldContent() {
        var standard = standard("Java 规范", true);
        version(standard, 1, "# 旧规范");
        service.snapshot(TASK, PROJECT, null);

        version(standard, 2, "# 新规范");
        var context = service.context(TASK);

        assertTrue(context.promptSection().contains("# 旧规范"));
        assertTrue(!context.promptSection().contains("# 新规范"));
    }

    @Test
    @DisplayName("快照文件缺失时拒绝执行")
    void rejectsMissingSnapshotFile() {
        var standard = standard("Java 规范", true);
        var version = version(standard, 1, "# 旧规范");
        service.snapshot(TASK, PROJECT, null);
        files.stored.remove(version.storagePath());

        assertThrows(DomainException.class, () -> service.context(TASK));
    }

    private CodeStandard standard(String name, boolean defaultApply) {
        return standards.save(CodeStandard.create(
                PROJECT, name, "说明", CodeStandardScope.BACKEND,
                true, defaultApply, 10));
    }

    private CodeStandardVersion version(CodeStandard standard, int versionNo, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        String path = files.store(
                com.pydlove.regent.shared.kernel.AttachmentScope.FILE_LIBRARY,
                OWNER.value(), ".md", bytes);
        LibraryDocument document = new LibraryDocument(
                LibraryDocumentId.of(versionNo),
                PROJECT,
                LibraryType.DEVELOPMENT_RULE,
                null,
                "java-" + versionNo + ".md",
                "text/markdown",
                bytes.length,
                hash,
                path,
                java.time.Instant.now());
        return versions.save(CodeStandardVersion.of(
                standard.id(), PROJECT, versionNo, document));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class TaskStandards implements
            com.pydlove.regent.execution.domain.repository.TaskCodeStandardRepository {
        private final Map<Long, TaskCodeStandard> store = new LinkedHashMap<>();
        private long sequence;

        @Override
        public TaskCodeStandard save(TaskCodeStandard standard) {
            TaskCodeStandard saved = standard.id() == null
                    ? standard.withIdentity(
                            com.pydlove.regent.execution.domain.model.TaskCodeStandardId.of(++sequence))
                    : standard;
            store.put(saved.id().value(), saved);
            return saved;
        }

        @Override
        public List<TaskCodeStandard> findByTask(TaskId taskId) {
            return store.values().stream()
                    .filter(standard -> standard.taskId().equals(taskId))
                    .toList();
        }

        @Override
        public Optional<TaskCodeStandard> findByIdOwned(
                com.pydlove.regent.execution.domain.model.TaskCodeStandardId id,
                UserId ownerId) {
            return Optional.ofNullable(store.get(id.value()));
        }
    }
}
