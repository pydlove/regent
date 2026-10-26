package com.pydlove.regent.delivery.interfaces.rest;

import com.pydlove.regent.delivery.application.command.InitiativeAttachmentService;
import com.pydlove.regent.delivery.application.command.UploadedInitiativeAttachment;
import com.pydlove.regent.delivery.domain.model.InitiativeAttachment;
import com.pydlove.regent.delivery.domain.model.InitiativeAttachmentId;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.interfaces.dto.InitiativeAttachmentResponse;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** 立项会议纪要附件上传、列表与内容下载。 */
@RestController
@RequestMapping("/api")
public class InitiativeAttachmentController {

    private static final CacheControl CONTENT_CACHE =
            CacheControl.maxAge(Duration.ofDays(7)).cachePrivate();

    private final InitiativeAttachmentService attachmentService;

    public InitiativeAttachmentController(InitiativeAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/initiatives/{initiativeId}/attachments")
    public InitiativeAttachmentResponse upload(
            @PathVariable long initiativeId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("没有收到会议纪要附件");
        }
        InitiativeAttachment saved = attachmentService.attach(
                InitiativeId.of(initiativeId), currentUser(request), toUploaded(file));
        return InitiativeAttachmentResponse.from(saved);
    }

    @GetMapping("/initiatives/{initiativeId}/attachments")
    public List<InitiativeAttachmentResponse> list(
            @PathVariable long initiativeId, HttpServletRequest request) {
        return attachmentService.list(InitiativeId.of(initiativeId), currentUser(request)).stream()
                .map(InitiativeAttachmentResponse::from)
                .toList();
    }

    @GetMapping("/initiative-attachments/{id}/content")
    public ResponseEntity<byte[]> content(
            @PathVariable long id, HttpServletRequest request) {
        InitiativeAttachment attachment = attachmentService.load(
                InitiativeAttachmentId.of(id), currentUser(request));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.contentType()))
                .cacheControl(CONTENT_CACHE)
                .body(attachmentService.content(attachment));
    }

    private static UploadedInitiativeAttachment toUploaded(MultipartFile file) {
        try {
            return new UploadedInitiativeAttachment(
                    file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException exception) {
            throw new DomainException("会议纪要附件读取失败：" + exception.getMessage());
        }
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }
}
