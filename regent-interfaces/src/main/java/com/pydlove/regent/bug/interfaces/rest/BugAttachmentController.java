package com.pydlove.regent.bug.interfaces.rest;

import com.pydlove.regent.bug.application.command.BugAttachmentService;
import com.pydlove.regent.bug.application.command.UploadedBugAttachment;
import com.pydlove.regent.bug.domain.model.BugAttachment;
import com.pydlove.regent.bug.domain.model.BugAttachmentId;
import com.pydlove.regent.bug.domain.model.BugId;
import com.pydlove.regent.bug.interfaces.dto.BugAttachmentResponse;
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

/** Bug 图片附件接口。上传和列表挂在 Bug 下，图片内容走独立出口。 */
@RestController
@RequestMapping("/api")
public class BugAttachmentController {

    private static final CacheControl IMAGE_CACHE = CacheControl.maxAge(Duration.ofDays(7)).cachePrivate();

    private final BugAttachmentService attachmentService;

    public BugAttachmentController(BugAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/bugs/{bugId}/attachments")
    public BugAttachmentResponse upload(@PathVariable long bugId,
                                        @RequestParam("file") MultipartFile file,
                                        HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("没有收到图片内容");
        }
        BugAttachment saved = attachmentService.attach(
                BugId.of(bugId), currentUser(request), toUploaded(file));
        return BugAttachmentResponse.from(saved);
    }

    @GetMapping("/bugs/{bugId}/attachments")
    public List<BugAttachmentResponse> list(@PathVariable long bugId, HttpServletRequest request) {
        return attachmentService.list(BugId.of(bugId), currentUser(request)).stream()
                .map(BugAttachmentResponse::from)
                .toList();
    }

    @GetMapping("/bug-attachments/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable long id, HttpServletRequest request) {
        BugAttachment attachment = attachmentService.load(
                BugAttachmentId.of(id), currentUser(request));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.contentType()))
                .cacheControl(IMAGE_CACHE)
                .body(attachmentService.content(attachment));
    }

    private static UploadedBugAttachment toUploaded(MultipartFile file) {
        try {
            return new UploadedBugAttachment(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new DomainException("上传的图片读不出来：" + e.getMessage());
        }
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }
}
