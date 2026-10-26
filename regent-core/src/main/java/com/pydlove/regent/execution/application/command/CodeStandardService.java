package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.application.query.CodeStandardView;
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
import com.pydlove.regent.execution.domain.repository.LibraryDocumentRepository;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 项目开发规范库。
 *
 * <p>文件内容继续复用项目文件库，开发规范只额外维护稳定条目、不可变版本和应用策略。
 * 这样未来需求文档、设计文档可以在同一文件库入口下扩展，而不会被迫继承规范版本语义。
 */
public class CodeStandardService {

    public static final int MAX_STANDARDS_PER_PROJECT = 20;
    private static final Set<String> STANDARD_EXTENSIONS = Set.of(
            ".md", ".txt", ".yaml", ".yml", ".json", ".xml");

    private final ProjectProfileRepository projectProfileRepository;
    private final CodeStandardRepository standardRepository;
    private final CodeStandardVersionRepository versionRepository;
    private final LibraryDocumentRepository libraryDocumentRepository;
    private final AttachmentStorage storage;
    private final TransactionRunner transactionRunner;
    private final long maxBytes;

    public CodeStandardService(ProjectProfileRepository projectProfileRepository,
                               CodeStandardRepository standardRepository,
                               CodeStandardVersionRepository versionRepository,
                               LibraryDocumentRepository libraryDocumentRepository,
                               AttachmentStorage storage,
                               TransactionRunner transactionRunner,
                               long maxBytes) {
        this.projectProfileRepository = projectProfileRepository;
        this.standardRepository = standardRepository;
        this.versionRepository = versionRepository;
        this.libraryDocumentRepository = libraryDocumentRepository;
        this.storage = storage;
        this.transactionRunner = transactionRunner;
        this.maxBytes = maxBytes;
    }

    public List<CodeStandardView> list(UserId ownerId, ProjectId projectId) {
        requireOwnedProject(ownerId, projectId);
        return standardRepository.findByProject(projectId).stream()
                .filter(standard -> !standard.archived())
                .sorted(Comparator.comparingInt(CodeStandard::sortOrder)
                        .thenComparing(standard -> standard.id().value()))
                .map(standard -> new CodeStandardView(
                        standard,
                        versionRepository.findCurrent(standard.id()).orElse(null)))
                .toList();
    }

    public CodeStandardView create(UserId ownerId,
                                   SaveCodeStandardCommand command,
                                   UploadedDocument uploaded) {
        requireOwnedProject(ownerId, command.projectId());
        validateCapacity(command.projectId(), 1);
        ensureNameAvailable(command.projectId(), command.name(), null);
        DocumentUploadPolicy.ValidatedDocument document = validateStandard(uploaded);

        PreparedFile prepared = prepareFile(ownerId, command.projectId(), document, uploaded.content());
        try {
            return transactionRunner.inTransaction(() -> {
                CodeStandard standard = standardRepository.save(CodeStandard.create(
                        command.projectId(),
                        command.name(),
                        command.description(),
                        command.scope(),
                        command.enabled(),
                        command.defaultApply(),
                        command.sortOrder()));
                LibraryDocument library = prepared.existing == null
                        ? libraryDocumentRepository.save(LibraryDocument.of(
                                command.projectId(),
                                LibraryType.DEVELOPMENT_RULE,
                                null,
                                document.filename(),
                                document.contentType(),
                                document.sizeBytes(),
                                document.contentHash(),
                                prepared.storedPath))
                        : prepared.existing;
                CodeStandardVersion version = versionRepository.save(
                        CodeStandardVersion.of(standard.id(), command.projectId(), 1, library));
                return new CodeStandardView(standard, version);
            });
        } catch (RuntimeException e) {
            if (prepared.storedPath != null) storage.delete(prepared.storedPath);
            throw e;
        }
    }

    public CodeStandardView update(UserId ownerId,
                                   CodeStandardId standardId,
                                   SaveCodeStandardCommand command) {
        CodeStandard standard = requireOwnedStandard(ownerId, standardId);
        if (!standard.projectId().equals(command.projectId())) {
            throw new DomainException("开发规范不能切换到其他项目");
        }
        ensureNameAvailable(command.projectId(), command.name(), standardId);
        CodeStandard updated = standardRepository.save(standard.update(
                command.name(), command.description(), command.scope(), command.enabled(),
                command.defaultApply(), command.sortOrder()));
        CodeStandardVersion current = versionRepository.findCurrent(standardId)
                .orElseThrow(() -> new DomainException("开发规范缺少有效版本：" + standardId.value()));
        return new CodeStandardView(updated, current);
    }

    public CodeStandardView setEnabled(UserId ownerId, CodeStandardId standardId, boolean enabled) {
        CodeStandard standard = requireOwnedStandard(ownerId, standardId);
        CodeStandard updated = standardRepository.save(enabled ? standard.enable() : standard.disable());
        CodeStandardVersion current = versionRepository.findCurrent(standardId)
                .orElseThrow(() -> new DomainException("开发规范缺少有效版本：" + standardId.value()));
        return new CodeStandardView(updated, current);
    }

    public void archive(UserId ownerId, CodeStandardId standardId) {
        CodeStandard standard = requireOwnedStandard(ownerId, standardId);
        standardRepository.save(standard.archive());
    }

    public CodeStandardView addVersion(UserId ownerId,
                                       CodeStandardId standardId,
                                       UploadedDocument uploaded) {
        CodeStandard standard = requireOwnedStandard(ownerId, standardId);
        if (standard.archived()) {
            throw new DomainException("已归档的开发规范不能上传新版本");
        }
        DocumentUploadPolicy.ValidatedDocument document = validateStandard(uploaded);
        CodeStandardVersion current = versionRepository.findCurrent(standardId).orElse(null);
        if (current != null && current.contentHash().equals(document.contentHash())) {
            return new CodeStandardView(standard, current);
        }

        PreparedFile prepared = prepareFile(ownerId, standard.projectId(), document, uploaded.content());
        try {
            return transactionRunner.inTransaction(() -> {
                LibraryDocument library = prepared.existing == null
                        ? libraryDocumentRepository.save(LibraryDocument.of(
                                standard.projectId(),
                                LibraryType.DEVELOPMENT_RULE,
                                null,
                                document.filename(),
                                document.contentType(),
                                document.sizeBytes(),
                                document.contentHash(),
                                prepared.storedPath))
                        : prepared.existing;
                CodeStandardVersion version = versionRepository.save(CodeStandardVersion.of(
                        standardId, standard.projectId(),
                        versionRepository.nextVersionNo(standardId), library));
                return new CodeStandardView(standard, version);
            });
        } catch (RuntimeException e) {
            if (prepared.storedPath != null) storage.delete(prepared.storedPath);
            throw e;
        }
    }

    public List<CodeStandardVersion> versions(UserId ownerId, CodeStandardId standardId) {
        requireOwnedStandard(ownerId, standardId);
        return versionRepository.findByStandard(standardId);
    }

    public byte[] content(UserId ownerId, CodeStandardVersionId versionId) {
        CodeStandardVersion version = loadVersion(ownerId, versionId);
        byte[] content = storage.read(version.storagePath());
        if (!sha256(content).equals(version.contentHash())) {
            throw new DomainException("开发规范文件摘要不一致：" + version.filename());
        }
        return content;
    }

    public CodeStandardVersion loadVersion(UserId ownerId, CodeStandardVersionId versionId) {
        return versionRepository.findByIdOwned(versionId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "开发规范版本不存在：" + versionId.value()));
    }

    public CodeStandardCopyResult copy(UserId ownerId,
                                       ProjectId sourceProjectId,
                                       ProjectId targetProjectId,
                                       List<CodeStandardId> standardIds,
                                       CodeStandardCopyStrategy strategy) {
        if (sourceProjectId.equals(targetProjectId)) {
            throw new DomainException("不能从当前项目复制到当前项目");
        }
        ProjectProfile sourceProject = requireOwnedProject(ownerId, sourceProjectId);
        requireOwnedProject(ownerId, targetProjectId);

        List<CodeStandard> source = standardRepository.findByProject(sourceProjectId).stream()
                .filter(standard -> !standard.archived())
                .filter(standard -> standardIds == null || standardIds.isEmpty()
                        || standardIds.contains(standard.id()))
                .toList();
        if (source.isEmpty()) {
            throw new DomainException("没有可复制的开发规范");
        }
        List<CodeStandardView> existingTarget = list(ownerId, targetProjectId);
        Set<String> targetHashes = new HashSet<>();
        Set<String> targetNames = new HashSet<>();
        for (CodeStandardView view : existingTarget) {
            targetNames.add(view.standard().name());
            if (view.currentVersion() != null) targetHashes.add(view.currentVersion().contentHash());
        }

        List<String> copied = new ArrayList<>();
        List<CodeStandardCopyResult.Skipped> skipped = new ArrayList<>();
        List<String> storedPaths = new ArrayList<>();
        try {
            List<CodeStandardView> created = transactionRunner.inTransaction(() -> {
                List<CodeStandardView> views = new ArrayList<>();
                int nextSort = existingTarget.stream()
                        .mapToInt(view -> view.standard().sortOrder())
                        .max()
                        .orElse(0) + 10;
                int remainingCapacity = MAX_STANDARDS_PER_PROJECT - existingTarget.size();
                for (CodeStandard item : source) {
                    CodeStandardVersion version = versionRepository.findCurrent(item.id())
                            .orElseThrow(() -> new DomainException(
                                    "开发规范缺少有效版本：" + item.name()));
                    if (targetHashes.contains(version.contentHash())) {
                        skipped.add(new CodeStandardCopyResult.Skipped(
                                item.name(), "目标项目已存在相同内容"));
                        continue;
                    }
                    String targetName = item.name();
                    if (targetNames.contains(targetName)) {
                        if (strategy != CodeStandardCopyStrategy.RENAME) {
                            skipped.add(new CodeStandardCopyResult.Skipped(
                                    item.name(), "目标项目存在同名规范"));
                            continue;
                        }
                        targetName = uniqueName(targetName, targetNames);
                    }
                    if (remainingCapacity <= 0) {
                        throw new DomainException("目标项目最多维护 "
                                + MAX_STANDARDS_PER_PROJECT + " 份开发规范");
                    }

                    byte[] content = storage.read(version.storagePath());
                    if (!sha256(content).equals(version.contentHash())) {
                        throw new DomainException(
                                "来源开发规范文件摘要不一致：" + item.name());
                    }
                    LibraryDocument existing = libraryDocumentRepository
                            .findByProjectAndTypeAndHash(
                                    targetProjectId, LibraryType.DEVELOPMENT_RULE, version.contentHash())
                            .orElse(null);
                    LibraryDocument library = existing;
                    if (library == null) {
                        String storedPath = storage.store(
                                AttachmentScope.FILE_LIBRARY,
                                ownerId.value(),
                                extensionOf(version.filename()),
                                content);
                        storedPaths.add(storedPath);
                        library = libraryDocumentRepository.save(LibraryDocument.of(
                                targetProjectId,
                                LibraryType.DEVELOPMENT_RULE,
                                null,
                                version.filename(),
                                version.contentType(),
                                version.sizeBytes(),
                                version.contentHash(),
                                storedPath));
                    }
                    CodeStandard copy = standardRepository.save(CodeStandard.copied(
                            targetProjectId,
                            targetName,
                            item.description(),
                            item.scope(),
                            item.enabled(),
                            item.defaultApply(),
                            nextSort,
                            sourceProject.id(),
                            item.id(),
                            version.id(),
                            version.versionNo()));
                    nextSort += 10;
                    CodeStandardVersion copiedVersion = versionRepository.save(
                            CodeStandardVersion.of(copy.id(), targetProjectId, 1, library));
                    views.add(new CodeStandardView(copy, copiedVersion));
                    copied.add(targetName);
                    targetNames.add(targetName);
                    targetHashes.add(version.contentHash());
                    remainingCapacity--;
                }
                return views;
            });
            return new CodeStandardCopyResult(copied, skipped);
        } catch (RuntimeException e) {
            for (String path : storedPaths) storage.delete(path);
            throw e;
        }
    }

    private PreparedFile prepareFile(UserId ownerId,
                                     ProjectId projectId,
                                     DocumentUploadPolicy.ValidatedDocument document,
                                     byte[] content) {
        LibraryDocument existing = libraryDocumentRepository
                .findByProjectAndTypeAndHash(projectId, LibraryType.DEVELOPMENT_RULE, document.contentHash())
                .orElse(null);
        if (existing != null) return new PreparedFile(existing, null, content);
        String storedPath = storage.store(
                AttachmentScope.FILE_LIBRARY, ownerId.value(), document.extension(), content);
        return new PreparedFile(null, storedPath, content);
    }

    private DocumentUploadPolicy.ValidatedDocument validateStandard(UploadedDocument uploaded) {
        if (uploaded == null) {
            throw new DomainException("没有收到开发规范文件");
        }
        DocumentUploadPolicy.ValidatedDocument document = DocumentUploadPolicy.validate(
                uploaded.filename(),
                uploaded.content() == null ? 0 : uploaded.content().length,
                uploaded.content(),
                maxBytes);
        if (!STANDARD_EXTENSIONS.contains(document.extension())) {
            throw new DomainException("开发规范只支持 Markdown、文本与结构化文本："
                    + String.join("、", STANDARD_EXTENSIONS));
        }
        return document;
    }

    private void validateCapacity(ProjectId projectId, int additional) {
        long active = standardRepository.findByProject(projectId).stream()
                .filter(standard -> !standard.archived())
                .count();
        if (active + additional > MAX_STANDARDS_PER_PROJECT) {
            throw new DomainException("每个项目最多维护 "
                    + MAX_STANDARDS_PER_PROJECT + " 份开发规范");
        }
    }

    private void ensureNameAvailable(ProjectId projectId, String name, CodeStandardId currentId) {
        standardRepository.findByProjectAndName(projectId, name == null ? "" : name.trim())
                .filter(existing -> currentId == null || !existing.id().equals(currentId))
                .ifPresent(existing -> {
                    throw new DomainException("当前项目已有同名开发规范：" + existing.name());
                });
    }

    private CodeStandard requireOwnedStandard(UserId ownerId, CodeStandardId id) {
        return standardRepository.findByIdOwned(id, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("开发规范不存在：" + id.value()));
    }

    private ProjectProfile requireOwnedProject(UserId ownerId, ProjectId projectId) {
        ProjectProfile project = projectProfileRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + projectId));
        if (project.ownerId() == null || !project.ownerId().equals(ownerId)) {
            throw new EntityNotFoundException("项目不存在：" + projectId);
        }
        return project;
    }

    private static String uniqueName(String base, Set<String> used) {
        int suffix = 2;
        String candidate = base + "（复制）";
        while (used.contains(candidate)) candidate = base + "（复制 " + suffix++ + "）";
        return candidate;
    }

    private static String extensionOf(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        return dot < 0 ? ".txt" : filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256", e);
        }
    }

    private record PreparedFile(LibraryDocument existing, String storedPath, byte[] content) {
    }
}
