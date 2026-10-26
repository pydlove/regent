package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.command.TaskDocumentService;
import com.pydlove.regent.execution.domain.model.LibraryDocumentId;
import com.pydlove.regent.execution.domain.model.TaskDocument;
import com.pydlove.regent.execution.domain.model.TaskDocumentId;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.support.TestCurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentControllerTest {

    private TaskDocumentService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(TaskDocumentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DocumentController(service))
                .addInterceptors(TestCurrentUser.interceptor())
                .build();
    }

    @Test
    @DisplayName("上传任务文档时透传是否保存到文件库")
    void uploadsDocument() throws Exception {
        TaskDocument document = new TaskDocument(
                TaskDocumentId.of(9L), TaskId.of(1L), LibraryDocumentId.of(3L),
                "接口说明.md", "text/markdown", 12, "/tmp/doc.md", Instant.now());
        when(service.attach(
                eq(TaskId.of(1L)),
                eq(UserId.of(TestCurrentUser.USER_ID)),
                org.mockito.ArgumentMatchers.any(),
                eq(true)))
                .thenReturn(document);

        mockMvc.perform(multipart("/api/tasks/1/documents")
                        .file(new MockMultipartFile(
                                "file", "接口说明.md", "text/markdown",
                                "# 接口".getBytes(StandardCharsets.UTF_8)))
                        .param("saveToLibrary", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libraryDocumentId").value(3))
                .andExpect(jsonPath("$.savedToLibrary").value(true));
    }

    @Test
    @DisplayName("从文件库绑定文档返回任务文档")
    void bindsLibraryDocuments() throws Exception {
        TaskDocument document = new TaskDocument(
                TaskDocumentId.of(10L), TaskId.of(1L), LibraryDocumentId.of(2L),
                "方案.pdf", "application/pdf", 20, "/tmp/plan.pdf", Instant.now());
        when(service.bindLibrary(
                eq(TaskId.of(1L)),
                eq(UserId.of(TestCurrentUser.USER_ID)),
                eq(List.of(LibraryDocumentId.of(2L)))))
                .thenReturn(List.of(document));

        mockMvc.perform(post("/api/tasks/1/documents/from-library")
                        .contentType("application/json")
                        .content("{\"documentIds\":[2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("方案.pdf"));
    }
}
