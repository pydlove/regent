package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.command.TaskDocumentService;
import com.pydlove.regent.execution.application.command.UploadedDocument;
import com.pydlove.regent.execution.domain.model.LibraryDocument;
import com.pydlove.regent.execution.domain.model.LibraryDocumentId;
import com.pydlove.regent.execution.domain.model.LibraryType;
import com.pydlove.regent.execution.domain.model.TaskDocument;
import com.pydlove.regent.execution.domain.model.TaskDocumentId;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.interfaces.dto.LibraryDocumentBindRequest;
import com.pydlove.regent.execution.interfaces.dto.LibraryDocumentResponse;
import com.pydlove.regent.execution.interfaces.dto.TaskDocumentResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 任务文档与项目文件库接口。 */
@RestController
@RequestMapping("/api")
public class DocumentController {

    private final TaskDocumentService documentService;

    public DocumentController(TaskDocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/tasks/{taskId}/documents")
    public TaskDocumentResponse upload(@PathVariable long taskId,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(defaultValue = "false") boolean saveToLibrary,
                                       HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("没有收到文档内容");
        }
        return TaskDocumentResponse.from(documentService.attach(
                TaskId.of(taskId), currentUser(request), toUploaded(file), saveToLibrary));
    }

    @GetMapping("/tasks/{taskId}/documents")
    public List<TaskDocumentResponse> taskDocuments(@PathVariable long taskId,
                                                    HttpServletRequest request) {
        return documentService.list(TaskId.of(taskId), currentUser(request)).stream()
                .map(TaskDocumentResponse::from)
                .toList();
    }

    @PostMapping("/tasks/{taskId}/documents/from-library")
    public List<TaskDocumentResponse> bindLibrary(@PathVariable long taskId,
                                                  @RequestBody LibraryDocumentBindRequest body,
                                                  HttpServletRequest request) {
        List<LibraryDocumentId> ids = body.documentIds() == null
                ? List.of()
                : body.documentIds().stream().map(id -> {
                    if (id == null) {
                        throw new DomainException("文件库文档 ID 不能为空");
                    }
                    return LibraryDocumentId.of(id);
                }).toList();
        return documentService.bindLibrary(TaskId.of(taskId), currentUser(request), ids).stream()
                .map(TaskDocumentResponse::from)
                .toList();
    }

    @GetMapping("/file-library")
    public List<LibraryDocumentResponse> library(@RequestParam long projectId,
                                                 @RequestParam(defaultValue = "GENERAL") String type,
                                                 HttpServletRequest request) {
        LibraryType libraryType;
        try {
            libraryType = LibraryType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new DomainException("文件库类型不正确：" + type);
        }
        return documentService.library(currentUser(request), ProjectId.of(projectId), libraryType).stream()
                .map(LibraryDocumentResponse::from)
                .toList();
    }

    @GetMapping("/documents/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable long id, HttpServletRequest request) {
        TaskDocument document = documentService.load(
                TaskDocumentId.of(id), currentUser(request));
        return download(document.filename(), document.contentType(),
                documentService.content(document.storagePath()));
    }

    @GetMapping("/file-library/{id}/content")
    public ResponseEntity<byte[]> libraryContent(@PathVariable long id,
                                                 HttpServletRequest request) {
        LibraryDocument document = documentService.loadLibrary(
                LibraryDocumentId.of(id), currentUser(request));
        return download(document.filename(), document.contentType(),
                documentService.content(document.storagePath()));
    }

    private static ResponseEntity<byte[]> download(String filename,
                                                   String contentType,
                                                   byte[] content) {
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(content);
    }

    private static UploadedDocument toUploaded(MultipartFile file) {
        try {
            return new UploadedDocument(
                    file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new DomainException("上传的文档读不出来：" + e.getMessage());
        }
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }
}
