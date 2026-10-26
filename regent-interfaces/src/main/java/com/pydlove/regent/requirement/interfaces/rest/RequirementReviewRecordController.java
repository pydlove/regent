package com.pydlove.regent.requirement.interfaces.rest;

import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.requirement.application.command.RequirementReviewRecordService;
import com.pydlove.regent.requirement.application.command.RequirementReviewDecision;
import com.pydlove.regent.requirement.application.command.SaveRequirementReviewCommand;
import com.pydlove.regent.requirement.application.command.UploadedRequirementReviewAttachment;
import com.pydlove.regent.requirement.domain.model.RequirementReviewAttachment;
import com.pydlove.regent.requirement.domain.model.RequirementReviewAttachmentId;
import com.pydlove.regent.requirement.domain.model.RequirementReviewRecordId;
import com.pydlove.regent.requirement.interfaces.dto.RequirementReviewDecisionRequest;
import com.pydlove.regent.requirement.interfaces.dto.RequirementReviewRecordResponse;
import com.pydlove.regent.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/** 线下需求评审记录与会议纪要附件。 */
@RestController
@RequestMapping("/api")
public class RequirementReviewRecordController {

    private static final CacheControl CONTENT_CACHE =
            CacheControl.maxAge(Duration.ofDays(7)).cachePrivate();

    private final RequirementReviewRecordService service;

    public RequirementReviewRecordController(RequirementReviewRecordService service) {
        this.service = service;
    }

    @PostMapping(
            value = "/product-versions/{versionId}/requirement-review-records",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RequirementReviewRecordResponse record(
            @PathVariable long versionId,
            @RequestParam String reviewDate,
            @RequestParam(required = false) String minutes,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            HttpServletRequest request) {
        return RequirementReviewRecordResponse.from(service.submit(
                ProductVersionId.of(versionId),
                currentUser(request),
                new SaveRequirementReviewCommand(
                        LocalDate.parse(reviewDate),
                        minutes),
                uploads(files)));
    }

    @PostMapping("/requirement-review-records/{id}/decision")
    public RequirementReviewRecordResponse decide(
            @PathVariable long id,
            @RequestBody RequirementReviewDecisionRequest body,
            HttpServletRequest request) {
        if (body == null || body.decision() == null || body.decision().isBlank()) {
            throw new IllegalArgumentException("缺少需求评审审核结论");
        }
        RequirementReviewDecision decision;
        try {
            decision = RequirementReviewDecision.valueOf(body.decision().trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("不支持的需求评审审核结论：" + body.decision());
        }
        return RequirementReviewRecordResponse.from(service.decide(
                RequirementReviewRecordId.of(id),
                currentUser(request),
                decision,
                body.comment()));
    }

    @GetMapping("/product-versions/{versionId}/requirement-review-records")
    public List<RequirementReviewRecordResponse> list(
            @PathVariable long versionId,
            HttpServletRequest request) {
        return service.list(ProductVersionId.of(versionId), currentUser(request)).stream()
                .map(RequirementReviewRecordResponse::from)
                .toList();
    }

    @GetMapping("/requirement-review-attachments/{id}/content")
    public ResponseEntity<byte[]> content(
            @PathVariable long id,
            HttpServletRequest request) {
        RequirementReviewAttachment attachment = service.loadAttachment(
                RequirementReviewAttachmentId.of(id), currentUser(request));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.contentType()))
                .cacheControl(CONTENT_CACHE)
                .body(service.content(attachment));
    }

    private static List<UploadedRequirementReviewAttachment> uploads(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return List.of();
        return files.stream().filter(file -> file != null && !file.isEmpty())
                .map(RequirementReviewRecordController::upload)
                .toList();
    }

    private static UploadedRequirementReviewAttachment upload(MultipartFile file) {
        try {
            return new UploadedRequirementReviewAttachment(
                    file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException exception) {
            throw new DomainException("需求评审纪要读取失败：" + exception.getMessage());
        }
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }
}
