package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.execution.application.command.TaskDocumentService;
import com.pydlove.regent.execution.application.command.UploadedDocument;
import com.pydlove.regent.execution.domain.model.TaskDocument;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.port.AttachmentStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 任务文档与文件库的真实 MySQL 往返验证。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskDocumentPersistenceIT {

    private static final long USER_ID = 9493L;
    private static final long PROJECT_ID = 9493L;
    private static final long TASK_ID = 9493L;
    private static final long SECOND_TASK_ID = 9494L;

    @Autowired
    private TaskDocumentService documentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AttachmentStorage storage;

    @Test
    @DisplayName("同一内容保存文件库只落一份，并可绑定到后续任务")
    void savesAndReusesLibraryDocument() {
        prepareData();
        List<String> paths = new ArrayList<>();
        try {
            UploadedDocument upload = new UploadedDocument(
                    "接口说明.md",
                    "text/markdown",
                    "# 接口说明\n请求字段如下".getBytes(StandardCharsets.UTF_8));

            TaskDocument first = documentService.attach(
                    TaskId.of(TASK_ID), UserId.of(USER_ID), upload, true);
            TaskDocument second = documentService.attach(
                    TaskId.of(SECOND_TASK_ID), UserId.of(USER_ID),
                    new UploadedDocument("同一份.md", upload.contentType(), upload.content()), true);
            paths.add(first.storagePath());

            assertEquals(first.libraryDocumentId(), second.libraryDocumentId());
            assertEquals(first.storagePath(), second.storagePath());
            assertEquals(1, documentService.library(
                    UserId.of(USER_ID), ProjectId.of(PROJECT_ID)).size());

            var bound = documentService.bindLibrary(
                    TaskId.of(TASK_ID), UserId.of(USER_ID),
                    List.of(first.libraryDocumentId()));
            assertTrue(bound.isEmpty(), "已经绑定过的文件库文档不应重复绑定");
        } finally {
            paths.forEach(storage::delete);
        }
    }

    private void prepareData() {
        jdbcTemplate.update("""
                INSERT INTO app_user (id, username, display_name, password_hash, created_at)
                VALUES (?, 'task_document_it', '任务文档测试', 'test-hash', UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE display_name = VALUES(display_name)
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO project
                    (id, owner_id, code, name, path, default_decision_level, enabled,
                     deleted, created_at, updated_at)
                VALUES (?, ?, 'task_document_it', '文档测试项目', '/tmp/task-document-it',
                        'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE owner_id = VALUES(owner_id), deleted = 0
                """, PROJECT_ID, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO task
                    (id, project_id, source, title, prompt, status, priority,
                     attempt_count, deleted, created_at, updated_at)
                VALUES (?, ?, 'MANUAL', '文档任务', '读取文档', 'PENDING', 0,
                        0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE deleted = 0
                """, TASK_ID, PROJECT_ID);
        jdbcTemplate.update("""
                INSERT INTO task
                    (id, project_id, source, title, prompt, status, priority,
                     attempt_count, deleted, created_at, updated_at)
                VALUES (?, ?, 'MANUAL', '复用文档任务', '读取同一份文档', 'PENDING', 0,
                        0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE deleted = 0
                """, SECOND_TASK_ID, PROJECT_ID);
    }
}
