package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.command.CodeStandardService;
import com.pydlove.regent.execution.application.command.SaveCodeStandardCommand;
import com.pydlove.regent.execution.application.command.TaskCodeStandardService;
import com.pydlove.regent.execution.application.command.UploadedDocument;
import com.pydlove.regent.execution.domain.model.CodeStandardCopyStrategy;
import com.pydlove.regent.execution.domain.model.CodeStandardId;
import com.pydlove.regent.execution.domain.model.CodeStandardScope;
import com.pydlove.regent.execution.domain.model.CodeStandardVersionId;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskCodeStandardId;
import com.pydlove.regent.execution.interfaces.dto.CodeStandardCopyRequest;
import com.pydlove.regent.execution.interfaces.dto.CodeStandardCopyResponse;
import com.pydlove.regent.execution.interfaces.dto.CodeStandardEnabledRequest;
import com.pydlove.regent.execution.interfaces.dto.CodeStandardResponse;
import com.pydlove.regent.execution.interfaces.dto.CodeStandardSaveRequest;
import com.pydlove.regent.execution.interfaces.dto.CodeStandardVersionResponse;
import com.pydlove.regent.execution.interfaces.dto.TaskCodeStandardResponse;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 项目开发规范库接口。 */
@RestController
@RequestMapping("/api")
public class CodeStandardController {

    private final CodeStandardService codeStandardService;
    private final TaskCodeStandardService taskCodeStandardService;

    public CodeStandardController(CodeStandardService codeStandardService,
                                  TaskCodeStandardService taskCodeStandardService) {
        this.codeStandardService = codeStandardService;
        this.taskCodeStandardService = taskCodeStandardService;
    }

    @GetMapping("/projects/{projectId}/code-standards")
    public List<CodeStandardResponse> list(@PathVariable long projectId,
                                           HttpServletRequest request) {
        return codeStandardService.list(currentUser(request), ProjectId.of(projectId)).stream()
                .map(CodeStandardResponse::from)
                .toList();
    }

    @PostMapping("/projects/{projectId}/code-standards")
    public CodeStandardResponse create(@PathVariable long projectId,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam String name,
                                       @RequestParam(required = false) String description,
                                       @RequestParam(defaultValue = "GENERAL") String scope,
                                       @RequestParam(defaultValue = "true") boolean enabled,
                                       @RequestParam(defaultValue = "true") boolean defaultApply,
                                       @RequestParam(defaultValue = "0") int sortOrder,
                                       HttpServletRequest request) {
        return CodeStandardResponse.from(codeStandardService.create(
                currentUser(request),
                command(projectId, name, description, scope, enabled, defaultApply, sortOrder),
                uploaded(file)));
    }

    @PutMapping("/code-standards/{id}")
    public CodeStandardResponse update(@PathVariable long id,
                                       @RequestBody CodeStandardSaveRequest body,
                                       HttpServletRequest request) {
        if (body.projectId() == null) {
            throw new DomainException("缺少 projectId");
        }
        return CodeStandardResponse.from(codeStandardService.update(
                currentUser(request),
                CodeStandardId.of(id),
                command(body.projectId(), body.name(), body.description(), body.scope(),
                        body.enabled() == null || body.enabled(),
                        body.defaultApply() == null || body.defaultApply(),
                        body.sortOrder() == null ? 0 : body.sortOrder())));
    }

    @PostMapping("/code-standards/{id}/versions")
    public CodeStandardResponse addVersion(@PathVariable long id,
                                           @RequestParam("file") MultipartFile file,
                                           HttpServletRequest request) {
        return CodeStandardResponse.from(codeStandardService.addVersion(
                currentUser(request), CodeStandardId.of(id), uploaded(file)));
    }

    @PostMapping("/code-standards/{id}/enabled")
    public CodeStandardResponse setEnabled(@PathVariable long id,
                                           @RequestBody CodeStandardEnabledRequest body,
                                           HttpServletRequest request) {
        return CodeStandardResponse.from(codeStandardService.setEnabled(
                currentUser(request), CodeStandardId.of(id), body.enabled()));
    }

    @PostMapping("/code-standards/{id}/archive")
    public void archive(@PathVariable long id, HttpServletRequest request) {
        codeStandardService.archive(currentUser(request), CodeStandardId.of(id));
    }

    @GetMapping("/code-standards/{id}/versions")
    public List<CodeStandardVersionResponse> versions(@PathVariable long id,
                                                      HttpServletRequest request) {
        return codeStandardService.versions(currentUser(request), CodeStandardId.of(id)).stream()
                .map(CodeStandardVersionResponse::from)
                .toList();
    }

    @GetMapping("/code-standard-versions/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable long id, HttpServletRequest request) {
        UserId ownerId = currentUser(request);
        var version = codeStandardService.loadVersion(
                ownerId, CodeStandardVersionId.of(id));
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(version.contentType());
        } catch (IllegalArgumentException e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(version.filename(),
                                StandardCharsets.UTF_8).build().toString())
                .body(codeStandardService.content(ownerId, version.id()));
    }

    @PostMapping("/projects/{targetProjectId}/code-standards/copy")
    public CodeStandardCopyResponse copy(@PathVariable long targetProjectId,
                                         @RequestBody CodeStandardCopyRequest body,
                                         HttpServletRequest request) {
        List<CodeStandardId> ids = body.standardIds() == null
                ? null
                : body.standardIds().stream().map(CodeStandardId::of).toList();
        CodeStandardCopyStrategy strategy;
        try {
            strategy = body.strategy() == null
                    ? CodeStandardCopyStrategy.SKIP
                    : CodeStandardCopyStrategy.valueOf(body.strategy());
        } catch (IllegalArgumentException e) {
            throw new DomainException("不支持的复制冲突策略：" + body.strategy());
        }
        return CodeStandardCopyResponse.from(codeStandardService.copy(
                currentUser(request),
                ProjectId.of(body.sourceProjectId()),
                ProjectId.of(targetProjectId),
                ids,
                strategy));
    }

    @GetMapping("/tasks/{taskId}/code-standards")
    public List<TaskCodeStandardResponse> taskStandards(@PathVariable long taskId,
                                                        HttpServletRequest request) {
        return taskCodeStandardService.listForTask(TaskId.of(taskId), currentUser(request)).stream()
                .map(TaskCodeStandardResponse::from)
                .toList();
    }

    @GetMapping("/task-code-standards/{id}/content")
    public ResponseEntity<byte[]> taskStandardContent(@PathVariable long id,
                                                      HttpServletRequest request) {
        // 任务快照内容通过任务归属校验；接口层按任务列表拿到对应快照后再读文件。
        UserId ownerId = currentUser(request);
        var snapshot = taskCodeStandardService.loadForTaskSnapshot(
                TaskCodeStandardId.of(id), ownerId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(snapshot.contentType());
        } catch (IllegalArgumentException e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(snapshot.filename(),
                                StandardCharsets.UTF_8).build().toString())
                .body(taskCodeStandardService.content(snapshot));
    }

    private static SaveCodeStandardCommand command(long projectId,
                                                   String name,
                                                   String description,
                                                   String scope,
                                                   boolean enabled,
                                                   boolean defaultApply,
                                                   int sortOrder) {
        CodeStandardScope parsed;
        try {
            parsed = scope == null ? CodeStandardScope.GENERAL
                    : CodeStandardScope.valueOf(scope);
        } catch (IllegalArgumentException e) {
            throw new DomainException("开发规范范围不正确：" + scope);
        }
        return new SaveCodeStandardCommand(ProjectId.of(projectId), name, description, parsed,
                enabled, defaultApply, sortOrder);
    }

    private static UploadedDocument uploaded(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new DomainException("没有收到开发规范文件");
        try {
            return new UploadedDocument(file.getOriginalFilename(), file.getContentType(),
                    file.getBytes());
        } catch (IOException e) {
            throw new DomainException("开发规范文件读取失败：" + e.getMessage());
        }
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }
}
