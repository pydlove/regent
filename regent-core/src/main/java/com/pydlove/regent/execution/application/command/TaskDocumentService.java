package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.LibraryDocument;
import com.pydlove.regent.execution.domain.model.LibraryDocumentId;
import com.pydlove.regent.execution.domain.model.LibraryType;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskDocument;
import com.pydlove.regent.execution.domain.model.TaskDocumentId;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.repository.LibraryDocumentRepository;
import com.pydlove.regent.execution.domain.repository.TaskDocumentRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.shared.domain.port.AttachmentStorage;
import com.pydlove.regent.shared.kernel.AttachmentScope;
import com.pydlove.regent.shared.kernel.DocumentUploadPolicy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 任务文档与项目文件库。
 *
 * <p>一次性文档落在任务目录；勾选“保存到文件库”时按项目内容哈希去重，任务与文件库引用
 * 同一个物理文件。从文件库复用时只新增任务绑定，不复制文件。
 */
public class TaskDocumentService {

    private static final int MAX_DOCUMENTS_PER_TASK = 20;

    private final TaskRepository taskRepository;
    private final TaskDocumentRepository taskDocumentRepository;
    private final LibraryDocumentRepository libraryDocumentRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final AttachmentStorage storage;
    private final TransactionRunner transactionRunner;
    private final long maxBytes;

    public TaskDocumentService(TaskRepository taskRepository,
                               TaskDocumentRepository taskDocumentRepository,
                               LibraryDocumentRepository libraryDocumentRepository,
                               ProjectProfileRepository projectProfileRepository,
                               AttachmentStorage storage,
                               TransactionRunner transactionRunner,
                               long maxBytes) {
        this.taskRepository = taskRepository;
        this.taskDocumentRepository = taskDocumentRepository;
        this.libraryDocumentRepository = libraryDocumentRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.storage = storage;
        this.transactionRunner = transactionRunner;
        this.maxBytes = maxBytes;
    }

    public TaskDocument attach(TaskId taskId,
                               UserId ownerId,
                               UploadedDocument uploaded,
                               boolean saveToLibrary) {
        Task task = requireTask(taskId, ownerId);
        ensureCapacity(taskId, 1);
        DocumentUploadPolicy.ValidatedDocument document = DocumentUploadPolicy.validate(
                uploaded.filename(), uploaded.content() == null ? 0 : uploaded.content().length,
                uploaded.content(), maxBytes);

        LibraryDocument existing = saveToLibrary
                ? libraryDocumentRepository
                .findByProjectAndTypeAndHash(
                        task.getProjectId(), LibraryType.GENERAL, document.contentHash())
                .orElse(null)
                : null;
        String storedPath = null;
        if (existing == null) {
            AttachmentScope scope = saveToLibrary
                    ? AttachmentScope.FILE_LIBRARY
                    : AttachmentScope.TASK_DOCUMENT;
            long scopeId = saveToLibrary ? ownerId.value() : taskId.value();
            storedPath = storage.store(scope, scopeId, document.extension(), uploaded.content());
        }

        String path = existing == null ? storedPath : existing.storagePath();
        try {
            return transactionRunner.inTransaction(() -> {
                LibraryDocument library = existing;
                if (saveToLibrary && library == null) {
                    library = libraryDocumentRepository.save(LibraryDocument.of(
                            task.getProjectId(),
                            LibraryType.GENERAL,
                            taskId,
                            document.filename(),
                            document.contentType(),
                            document.sizeBytes(),
                            document.contentHash(),
                            path));
                }
                return taskDocumentRepository.save(TaskDocument.of(
                        taskId,
                        library == null ? null : library.id(),
                        document.filename(),
                        document.contentType(),
                        document.sizeBytes(),
                        path));
            });
        } catch (RuntimeException e) {
            if (storedPath != null) {
                storage.delete(storedPath);
            }
            throw e;
        }
    }

    public List<TaskDocument> list(TaskId taskId, UserId ownerId) {
        requireTask(taskId, ownerId);
        return taskDocumentRepository.findByTask(taskId);
    }

    public List<LibraryDocument> library(UserId ownerId, ProjectId projectId) {
        return library(ownerId, projectId, LibraryType.GENERAL);
    }

    public List<LibraryDocument> library(UserId ownerId, ProjectId projectId, LibraryType libraryType) {
        requireOwnedProject(ownerId, projectId);
        return libraryDocumentRepository.findByProjectAndType(projectId, libraryType);
    }

    public List<TaskDocument> bindLibrary(TaskId taskId,
                                          UserId ownerId,
                                          List<LibraryDocumentId> libraryIds) {
        if (libraryIds == null || libraryIds.isEmpty()) {
            return List.of();
        }
        Task task = requireTask(taskId, ownerId);
        LinkedHashSet<LibraryDocumentId> unique = new LinkedHashSet<>(libraryIds);
        List<TaskDocument> existing = taskDocumentRepository.findByTask(taskId);
        List<LibraryDocumentId> alreadyBound = existing.stream()
                .map(TaskDocument::libraryDocumentId)
                .filter(java.util.Objects::nonNull)
                .toList();

        List<LibraryDocument> documents = new ArrayList<>();
        for (LibraryDocumentId id : unique) {
            if (alreadyBound.contains(id)) {
                continue;
            }
            LibraryDocument document = libraryDocumentRepository.findByIdOwned(id, ownerId)
                    .orElseThrow(() -> new EntityNotFoundException("文件库文档不存在：" + id));
            if (!document.projectId().equals(task.getProjectId())) {
                throw new DomainException("不能把其他项目的文档绑定到当前任务：" + id);
            }
            documents.add(document);
        }
        if (existing.size() + documents.size() > MAX_DOCUMENTS_PER_TASK) {
            throw new DomainException("单个任务最多绑定 " + MAX_DOCUMENTS_PER_TASK + " 份文档");
        }
        return transactionRunner.inTransaction(() -> documents.stream()
                .map(document -> taskDocumentRepository.save(TaskDocument.of(
                        taskId,
                        document.id(),
                        document.filename(),
                        document.contentType(),
                        document.sizeBytes(),
                        document.storagePath())))
                .toList());
    }

    public TaskDocument load(TaskDocumentId id, UserId ownerId) {
        return taskDocumentRepository.findByIdOwned(id, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("任务文档不存在：" + id));
    }

    public LibraryDocument loadLibrary(LibraryDocumentId id, UserId ownerId) {
        return libraryDocumentRepository.findByIdOwned(id, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("文件库文档不存在：" + id));
    }

    public byte[] content(String storagePath) {
        return storage.read(storagePath);
    }

    private Task requireTask(TaskId taskId, UserId ownerId) {
        return taskRepository.findByIdOwned(taskId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + taskId));
    }

    private void ensureCapacity(TaskId taskId, int additional) {
        int current = taskDocumentRepository.findByTask(taskId).size();
        if (current + additional > MAX_DOCUMENTS_PER_TASK) {
            throw new DomainException("单个任务最多绑定 " + MAX_DOCUMENTS_PER_TASK + " 份文档");
        }
    }

    private ProjectProfile requireOwnedProject(UserId ownerId, ProjectId projectId) {
        ProjectProfile project = projectProfileRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + projectId));
        if (project.ownerId() == null || !project.ownerId().equals(ownerId)) {
            throw new EntityNotFoundException("项目不存在：" + projectId);
        }
        return project;
    }
}
