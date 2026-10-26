package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.CodeStandard;
import com.pydlove.regent.execution.domain.model.CodeStandardId;
import com.pydlove.regent.execution.domain.model.CodeStandardVersion;
import com.pydlove.regent.execution.domain.model.TaskCodeStandard;
import com.pydlove.regent.execution.domain.model.TaskCodeStandardId;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.repository.CodeStandardRepository;
import com.pydlove.regent.execution.domain.repository.CodeStandardVersionRepository;
import com.pydlove.regent.execution.domain.repository.TaskCodeStandardRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.port.AttachmentStorage;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 把项目开发规范固定到任务，并在执行前把正文交给执行器。
 *
 * <p>规范不是普通附件：任务只保存创建时的版本快照；派发前校验文件与摘要；执行时直接
 * 注入正文，确保内部 Codex 与外部设备代理拿到的是同一份内容，不依赖两边共享文件系统。
 */
public class TaskCodeStandardService {

    private final TaskRepository taskRepository;
    private final CodeStandardRepository standardRepository;
    private final CodeStandardVersionRepository versionRepository;
    private final TaskCodeStandardRepository taskCodeStandardRepository;
    private final AttachmentStorage storage;
    private final long maxPromptBytes;

    public TaskCodeStandardService(TaskRepository taskRepository,
                                   CodeStandardRepository standardRepository,
                                   CodeStandardVersionRepository versionRepository,
                                   TaskCodeStandardRepository taskCodeStandardRepository,
                                   AttachmentStorage storage,
                                   long maxPromptBytes) {
        this.taskRepository = taskRepository;
        this.standardRepository = standardRepository;
        this.versionRepository = versionRepository;
        this.taskCodeStandardRepository = taskCodeStandardRepository;
        this.storage = storage;
        this.maxPromptBytes = maxPromptBytes;
    }

    /**
     * 创建任务时的规范快照。{@code requestedIds == null} 表示采用项目默认规范；
     * 空列表表示用户明确选择本次不使用规范。
     */
    public List<TaskCodeStandard> snapshot(TaskId taskId,
                                           ProjectId projectId,
                                           List<CodeStandardId> requestedIds) {
        List<CodeStandard> active = standardRepository.findByProject(projectId).stream()
                .filter(standard -> !standard.archived())
                .toList();
        Map<CodeStandardId, CodeStandard> byId = active.stream()
                .collect(Collectors.toMap(CodeStandard::id, Function.identity()));

        List<CodeStandard> selected;
        if (requestedIds == null) {
            selected = active.stream()
                    .filter(standard -> standard.enabled() && standard.defaultApply())
                    .sorted(Comparator.comparingInt(CodeStandard::sortOrder)
                            .thenComparing(standard -> standard.id().value()))
                    .toList();
        } else if (requestedIds.isEmpty()) {
            selected = List.of();
        } else {
            LinkedHashSet<CodeStandardId> unique = new LinkedHashSet<>(requestedIds);
            selected = new ArrayList<>();
            for (CodeStandardId id : unique) {
                CodeStandard standard = byId.get(id);
                if (standard == null) {
                    throw new DomainException("开发规范不属于当前项目：" + id.value());
                }
                standard.requireUsable();
                selected.add(standard);
            }
        }

        validateTotalSize(selected);
        List<TaskCodeStandard> snapshots = new ArrayList<>();
        for (CodeStandard standard : selected) {
            CodeStandardVersion version = versionRepository.findCurrent(standard.id())
                    .orElseThrow(() -> new DomainException(
                            "开发规范缺少有效版本：" + standard.name()));
            validateContent(version);
            snapshots.add(taskCodeStandardRepository.save(
                    TaskCodeStandard.of(taskId, standard, version)));
        }
        return snapshots;
    }

    public List<TaskCodeStandard> listForTask(TaskId taskId, UserId ownerId) {
        taskRepository.findByIdOwned(taskId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + taskId));
        return taskCodeStandardRepository.findByTask(taskId);
    }

    public TaskCodeStandard loadForTaskSnapshot(TaskCodeStandardId id, UserId ownerId) {
        return taskCodeStandardRepository.findByIdOwned(id, ownerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "任务开发规范快照不存在：" + id.value()));
    }

    public byte[] content(TaskCodeStandard snapshot) {
        byte[] content = storage.read(snapshot.storagePath());
        if (!sha256(content).equals(snapshot.contentHash())) {
            throw new DomainException("任务开发规范文件摘要不一致：" + snapshot.filename());
        }
        return content;
    }

    public CodeStandardContext context(TaskId taskId) {
        List<TaskCodeStandard> snapshots = taskCodeStandardRepository.findByTask(taskId);
        if (snapshots.isEmpty()) return CodeStandardContext.empty();

        long total = 0;
        StringBuilder prompt = new StringBuilder("\n\n---\n\n## 项目开发规范\n")
                .append("以下规范是本任务的强制约束。执行前必须完整阅读并在实现、测试与自检中遵守。\n")
                .append("不得修改或删除规范内容；如任务要求与规范冲突，必须挂起决策。\n");
        List<String> auditItems = new ArrayList<>();
        for (TaskCodeStandard snapshot : snapshots) {
            byte[] content = storage.read(snapshot.storagePath());
            total += content.length;
            if (total > maxPromptBytes) {
                throw new DomainException("任务关联的开发规范总大小超过执行上限："
                        + maxPromptBytes + " 字节，请拆分或精简规范");
            }
            if (!sha256(content).equals(snapshot.contentHash())) {
                throw new DomainException("开发规范文件摘要不一致，拒绝执行：" + snapshot.standardName());
            }
            String text = decodeUtf8(content, snapshot.standardName());
            prompt.append("\n### 《").append(snapshot.standardName()).append("》 v")
                    .append(snapshot.versionNo()).append('\n')
                    .append("文件：").append(snapshot.filename()).append('\n')
                    .append("SHA-256：").append(snapshot.contentHash()).append("\n\n")
                    .append("<development-rule>\n").append(text)
                    .append(text.endsWith("\n") ? "" : "\n")
                    .append("</development-rule>\n");
            auditItems.add("{\"standardId\":" + snapshot.standardId().value()
                    + ",\"versionId\":" + snapshot.standardVersionId().value()
                    + ",\"version\":" + snapshot.versionNo()
                    + ",\"name\":\"" + json(snapshot.standardName())
                    + "\",\"sha256\":\"" + snapshot.contentHash() + "\"}");
        }
        return new CodeStandardContext(prompt.toString(),
                "{\"standards\":[" + String.join(",", auditItems) + "]}");
    }

    public void requireReady(TaskId taskId) {
        context(taskId);
    }

    private void validateTotalSize(List<CodeStandard> standards) {
        long total = 0;
        for (CodeStandard standard : standards) {
            CodeStandardVersion version = versionRepository.findCurrent(standard.id())
                    .orElseThrow(() -> new DomainException(
                            "开发规范缺少有效版本：" + standard.name()));
            total += version.sizeBytes();
        }
        if (total > maxPromptBytes) {
            throw new DomainException("本次选择的开发规范总大小超过上限："
                    + maxPromptBytes + " 字节，请减少选择或拆分规范");
        }
    }

    private void validateContent(CodeStandardVersion version) {
        byte[] content = storage.read(version.storagePath());
        if (!sha256(content).equals(version.contentHash())) {
            throw new DomainException("开发规范文件摘要不一致：" + version.filename());
        }
        decodeUtf8(content, version.filename());
    }

    private static String decodeUtf8(byte[] content, String name) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new DomainException("开发规范不是有效 UTF-8 文本：" + name);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256", e);
        }
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    public record CodeStandardContext(String promptSection, String auditPayload) {

        public static CodeStandardContext empty() {
            return new CodeStandardContext("", "{\"standards\":[]}");
        }

        public boolean isEmpty() {
            return promptSection == null || promptSection.isBlank();
        }
    }
}
