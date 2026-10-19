package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.command.TaskAttachmentService;
import com.pydlove.regent.execution.application.command.UploadedAttachment;
import com.pydlove.regent.execution.domain.model.AttachmentId;
import com.pydlove.regent.execution.domain.model.TaskAttachment;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.interfaces.dto.AttachmentResponse;
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

/**
 * 附件接口。上传、列表、看图。
 *
 * <p>上传挂在任务下（{@code /api/tasks/{id}/attachments}）而不是做成「先传再绑」：
 * 绑定式接口要先造一批没人认领的文件和行，再想办法清理它们 —— 清理逻辑是最容易漏的那类代码。
 * 先建任务再贴图，多一次请求，但库里永远不会有不属于任何任务的图片。
 */
@RestController
@RequestMapping("/api")
public class AttachmentController {

    /** 图片不会变，让浏览器缓存住：每次重渲染都重新拉一遍几兆的截图没有意义。 */
    private static final CacheControl IMAGE_CACHE = CacheControl.maxAge(Duration.ofDays(7)).cachePrivate();

    private final TaskAttachmentService attachmentService;

    public AttachmentController(TaskAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/tasks/{taskId}/attachments")
    public AttachmentResponse upload(@PathVariable long taskId,
                                     @RequestParam("file") MultipartFile file,
                                     HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("没有收到图片内容");
        }
        TaskAttachment saved = attachmentService.attach(
                TaskId.of(taskId), currentUser(request), toUploaded(file));
        return AttachmentResponse.from(saved);
    }

    @GetMapping("/tasks/{taskId}/attachments")
    public List<AttachmentResponse> list(@PathVariable long taskId, HttpServletRequest request) {
        return attachmentService.list(TaskId.of(taskId), currentUser(request)).stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    /**
     * 原图。返回字节流而不是 JSON —— 前端直接把这个地址塞进 {@code <img src>}。
     *
     * <p>刻意不设 {@code Content-Disposition}：带上原文件名就要处理中文在 header 里的编码
     * （RFC 6266 的 {@code filename*}），而它的唯一用途是「另存为」时给个默认名 ——
     * 这一点用 {@code <a download>} 在前端就能解决。
     */
    @GetMapping("/attachments/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable long id, HttpServletRequest request) {
        TaskAttachment attachment = attachmentService.load(AttachmentId.of(id), currentUser(request));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.contentType()))
                .cacheControl(IMAGE_CACHE)
                .body(attachmentService.content(attachment));
    }

    private static UploadedAttachment toUploaded(MultipartFile file) {
        try {
            return new UploadedAttachment(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new DomainException("上传的图片读不出来：" + e.getMessage());
        }
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }
}
