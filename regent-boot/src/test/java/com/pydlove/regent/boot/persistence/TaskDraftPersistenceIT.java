package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.execution.domain.model.TaskDraft;
import com.pydlove.regent.execution.domain.repository.TaskDraftRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 任务草稿在真实 MySQL 上的往返、覆盖与删除。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskDraftPersistenceIT {

    private static final long USER_ID = 9101L;
    private static final long PROJECT_ID = 9102L;

    @Autowired
    private TaskDraftRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 准备用户与项目() {
        jdbcTemplate.update("""
                INSERT INTO app_user
                    (id, username, display_name, password_hash, created_at)
                VALUES (?, 'draft_it_user', '草稿测试用户', 'not-a-real-hash', UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE display_name = VALUES(display_name)
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO project
                    (id, code, name, path, default_decision_level, enabled, deleted, created_at, updated_at)
                VALUES (?, 'draft_it_project', '草稿测试项目', '/tmp/draft-it', 'L1', 1, 0,
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE name = VALUES(name)
                """, PROJECT_ID);
    }

    @Test
    @DisplayName("空白草稿可以保存，后续整体覆盖并保留创建时间")
    void 保存后覆盖当前用户草稿() {
        UserId userId = UserId.of(USER_ID);
        TaskDraft draft = TaskDraft.create(userId);
        repository.save(draft);

        draft.update(ProjectId.of(PROJECT_ID), "写一半的任务", 7,
                List.of(PluginId.of(8L), PluginId.of(9L)));
        repository.save(draft);

        TaskDraft loaded = repository.findByUserId(userId).orElseThrow();
        assertEquals(draft.getId(), loaded.getId());
        assertEquals("写一半的任务", loaded.getPrompt());
        assertEquals(ProjectId.of(PROJECT_ID), loaded.getProjectId().orElseThrow());
        assertEquals(List.of(PluginId.of(8L), PluginId.of(9L)), loaded.getPluginIds());
        assertEquals(draft.getCreatedAt(), loaded.getCreatedAt());
    }

    @Test
    @DisplayName("删除只移除当前用户的草稿")
    void 删除当前草稿() {
        UserId userId = UserId.of(USER_ID);
        TaskDraft draft = TaskDraft.create(userId);
        draft.update(null, "待删除", 0, List.of());
        repository.save(draft);

        repository.deleteByUserId(userId);

        assertTrue(repository.findByUserId(userId).isEmpty());
    }
}
