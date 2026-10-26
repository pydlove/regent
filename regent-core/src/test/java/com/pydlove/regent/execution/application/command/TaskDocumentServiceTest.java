package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.kernel.DocumentUploadPolicy;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskDocumentServiceTest {

    private static final UserId OWNER = UserId.of(1L);
    private static final ProjectId PROJECT = ProjectId.of(1L);

    private final ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
    private final ExecutionFakes.Runs runs = new ExecutionFakes.Runs();
    private final ExecutionFakes.Documents documents = new ExecutionFakes.Documents();
    private final ExecutionFakes.Projects projects = new ExecutionFakes.Projects();
    private final ExecutionFakes.AttachmentFiles files = new ExecutionFakes.AttachmentFiles();
    private final ExecutionFakes.Transactions transactions = new ExecutionFakes.Transactions();
    private TaskDocumentService service;

    @BeforeEach
    void setUp() {
        tasks.wire(runs);
        projects.put(new com.pydlove.regent.project.domain.repository.ProjectProfile(
                PROJECT, OWNER, "p1", "项目 1", "/tmp/p1",
                com.pydlove.regent.shared.kernel.DecisionLevel.L1, true,
                com.pydlove.regent.project.domain.model.ProjectSummaryStatus.NOT_GENERATED,
                null, null, null));
        tasks.add(ExecutionFakes.task(1L, PROJECT, TaskStatus.PENDING));
        tasks.add(ExecutionFakes.task(2L, PROJECT, TaskStatus.PENDING));
        service = new TaskDocumentService(
                tasks, documents, documents, projects, files, transactions,
                DocumentUploadPolicy.DEFAULT_MAX_BYTES);
    }

    @Test
    @DisplayName("一次性文档只绑定任务，不进入文件库")
    void attachesOneOffDocument() {
        var document = service.attach(TaskId.of(1L), OWNER, md("需求.md", "# 需求"), false);

        assertEquals(TaskId.of(1L), document.taskId());
        assertFalse(document.libraryDocumentId() != null);
        assertTrue(documents.allLibraryDocuments().isEmpty());
        assertTrue(files.stored.containsKey(document.storagePath()));
    }

    @Test
    @DisplayName("保存到文件库后任务与文件库引用同一份物理文件")
    void savesToLibrary() {
        var document = service.attach(TaskId.of(1L), OWNER, md("需求.md", "# 需求"), true);

        assertNotNull(document.libraryDocumentId());
        assertEquals(1, documents.allLibraryDocuments().size());
        assertEquals(
                documents.allLibraryDocuments().get(0).storagePath(),
                document.storagePath());
    }

    @Test
    @DisplayName("同一项目相同内容重复保存时文件库去重复用")
    void deduplicatesLibraryContent() {
        var first = service.attach(TaskId.of(1L), OWNER, md("需求.md", "# 同一份"), true);
        var second = service.attach(TaskId.of(2L), OWNER, md("改名.md", "# 同一份"), true);

        assertEquals(first.libraryDocumentId(), second.libraryDocumentId());
        assertEquals(first.storagePath(), second.storagePath());
        assertEquals(1, documents.allLibraryDocuments().size());
        assertEquals(1, files.stored.size());
    }

    @Test
    @DisplayName("文件库文档可以绑定到新任务且不复制文件")
    void bindsLibraryDocument() {
        var saved = service.attach(TaskId.of(1L), OWNER, md("需求.md", "# 需求"), true);

        var bound = service.bindLibrary(
                TaskId.of(2L), OWNER, java.util.List.of(saved.libraryDocumentId()));

        assertEquals(1, bound.size());
        assertEquals(saved.storagePath(), bound.get(0).storagePath());
        assertEquals(saved.libraryDocumentId(), bound.get(0).libraryDocumentId());
        assertEquals(1, files.stored.size());
    }

    private static UploadedDocument md(String filename, String content) {
        return new UploadedDocument(
                filename,
                "text/markdown",
                content.getBytes(StandardCharsets.UTF_8));
    }
}
