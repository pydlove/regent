package com.pydlove.regent.requirement.interfaces.rest;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.requirement.application.command.RequirementReviewRecordService;
import com.pydlove.regent.requirement.domain.model.RequirementReviewAttachment;
import com.pydlove.regent.requirement.domain.model.RequirementReviewAttachmentId;
import com.pydlove.regent.requirement.domain.model.RequirementReviewRecordId;
import com.pydlove.regent.support.TestCurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequirementReviewRecordControllerTest {

    private RequirementReviewRecordService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(RequirementReviewRecordService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new RequirementReviewRecordController(service))
                .addInterceptors(TestCurrentUser.interceptor())
                .build();
    }

    @Test
    @DisplayName("下载需求评审附件时返回原文件名")
    void downloadsReviewAttachmentWithOriginalFilename() throws Exception {
        byte[] content = "评审纪要".getBytes(StandardCharsets.UTF_8);
        RequirementReviewAttachment attachment = new RequirementReviewAttachment(
                RequirementReviewAttachmentId.of(9L),
                RequirementReviewRecordId.of(3L),
                "第1次需求评审.pdf",
                "application/pdf",
                content.length,
                "/tmp/review.pdf",
                UserId.of(TestCurrentUser.USER_ID),
                Instant.parse("2026-10-26T02:00:00Z"));
        when(service.loadAttachment(
                eq(RequirementReviewAttachmentId.of(9L)),
                eq(UserId.of(TestCurrentUser.USER_ID))))
                .thenReturn(attachment);
        when(service.content(attachment)).thenReturn(content);

        mockMvc.perform(get("/api/requirement-review-attachments/9/content")
                        .queryParam("download", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment")))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename*=UTF-8''")))
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(content));
    }
}
