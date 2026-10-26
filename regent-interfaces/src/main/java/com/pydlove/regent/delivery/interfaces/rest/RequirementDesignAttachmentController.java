package com.pydlove.regent.delivery.interfaces.rest;

import com.pydlove.regent.delivery.application.command.RequirementDesignAttachmentService;
import com.pydlove.regent.delivery.application.command.UploadedRequirementDesignAttachment;
import com.pydlove.regent.delivery.domain.model.RequirementDeliveryId;
import com.pydlove.regent.delivery.domain.model.RequirementDesignAttachment;
import com.pydlove.regent.delivery.domain.model.RequirementDesignAttachmentId;
import com.pydlove.regent.delivery.domain.model.RequirementDesignModuleId;
import com.pydlove.regent.delivery.interfaces.dto.RequirementDeliveryResponse;
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

/** 概要与模块详细设计附件。 */
@RestController
@RequestMapping("/api")
public class RequirementDesignAttachmentController {

    private static final CacheControl CONTENT_CACHE =
            CacheControl.maxAge(Duration.ofDays(7)).cachePrivate();

    private final RequirementDesignAttachmentService service;

    public RequirementDesignAttachmentController(
            RequirementDesignAttachmentService service) {
        this.service = service;
    }

    @PostMapping("/requirement-deliveries/{deliveryId}/design-attachments")
    public RequirementDeliveryResponse.DesignAttachmentResponse upload(
            @PathVariable long deliveryId,
            @RequestParam(required = false) Long moduleId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("没有收到设计附件");
        }
        RequirementDesignAttachment attachment = moduleId == null
                ? service.uploadOutline(
                        RequirementDeliveryId.of(deliveryId),
                        currentUser(request),
                        toUploaded(file))
                : service.uploadModule(
                        RequirementDeliveryId.of(deliveryId),
                        RequirementDesignModuleId.of(moduleId),
                        currentUser(request),
                        toUploaded(file));
        return RequirementDeliveryResponse.DesignAttachmentResponse.from(attachment);
    }

    @GetMapping("/requirement-design-attachments/{id}/content")
    public ResponseEntity<byte[]> content(
            @PathVariable long id,
            @RequestParam long deliveryId,
            HttpServletRequest request) {
        RequirementDesignAttachment attachment = service.load(
                RequirementDesignAttachmentId.of(id),
                RequirementDeliveryId.of(deliveryId),
                currentUser(request));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.contentType()))
                .cacheControl(CONTENT_CACHE)
                .body(service.content(attachment));
    }

    private static UploadedRequirementDesignAttachment toUploaded(MultipartFile file) {
        try {
            return new UploadedRequirementDesignAttachment(
                    file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException exception) {
            throw new DomainException("设计附件读取失败：" + exception.getMessage());
        }
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }
}
