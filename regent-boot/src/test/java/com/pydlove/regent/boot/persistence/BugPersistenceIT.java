package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.bug.domain.model.Bug;
import com.pydlove.regent.bug.domain.model.BugSeverity;
import com.pydlove.regent.bug.domain.model.BugSource;
import com.pydlove.regent.bug.domain.repository.BugPage;
import com.pydlove.regent.bug.domain.repository.BugQuery;
import com.pydlove.regent.bug.domain.repository.BugQueryRepository;
import com.pydlove.regent.bug.domain.repository.BugRepository;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 缺陷单在 MySQL 上的往返与来源关联。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BugPersistenceIT {

    private static final long PROBE_PROJECT_ID = 9998L;

    @Autowired
    private BugRepository bugRepository;

    @Autowired
    private BugQueryRepository bugQueryRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 准备探针项目() {
        jdbcTemplate.update("""
                INSERT INTO project (id, code, name, path, default_decision_level, enabled, deleted, created_at, updated_at)
                VALUES (?, 'bug_it_probe', 'Bug IT 探针项目', '/tmp/bug-it', 'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE name = VALUES(name)
                """, PROBE_PROJECT_ID);
    }

    @Test
    @DisplayName("人工缺陷往返后字段一致")
    void 人工缺陷往返() {
        Bug bug = Bug.reportManually(ProjectId.of(PROBE_PROJECT_ID), "导出乱码", BugSeverity.MINOR,
                "导出后打开", "src/Csv.java", "缺 UTF-8 BOM");

        bugRepository.save(bug);
        Bug loaded = bugRepository.findById(bug.getId()).orElseThrow();

        assertNotNull(bug.getId());
        assertEquals(BugSource.MANUAL, loaded.getSource());
        assertEquals("导出乱码", loaded.getTitle());
        assertEquals("src/Csv.java", loaded.getFilePath().orElseThrow());
    }

    @Test
    @DisplayName("AI 缺陷保留来源任务并出现在未解决查询中")
    void ai缺陷关联来源任务() {
        Task task = Task.manual(ProjectId.of(PROBE_PROJECT_ID), "IT 来源任务", "执行", 0);
        taskRepository.save(task);
        Bug bug = Bug.discoveredByAi(ProjectId.of(PROBE_PROJECT_ID), null, task.getId(),
                "分页错位", BugSeverity.MAJOR, "翻页后重复", "src/page.ts", "offset 算错");

        bugRepository.save(bug);
        BugPage page = bugQueryRepository.findPage(
                BugQuery.of(null, "OPEN", null, "分页错位", 1, 20));
        BugPage scoped = bugQueryRepository.findPage(
                BugQuery.of(null, "OPEN", PROBE_PROJECT_ID, "分页错位", 1, 20));
        BugPage otherProject = bugQueryRepository.findPage(
                BugQuery.of(null, "OPEN", PROBE_PROJECT_ID + 1, "分页错位", 1, 20));

        assertEquals(1, page.total());
        assertEquals(1, scoped.total());
        assertEquals(0, otherProject.total());
        assertEquals(BugSource.AI_DISCOVERED, page.items().get(0).source());
        assertEquals(task.getId().value(), page.items().get(0).sourceTaskId());
        assertEquals(TaskId.of(page.items().get(0).sourceTaskId()), bug.getSourceTaskId().orElseThrow());
    }

    @Test
    @DisplayName("列表按添加顺序倒序，最新添加的在前")
    void 列表按最新添加排序() {
        String prefix = "IT_BUG_SORT_" + System.nanoTime();
        insertBug(prefix + "-first", "MINOR", "OPEN", 30);
        insertBug(prefix + "-second", "MAJOR", "FIXED", 10);
        insertBug(prefix + "-third", "BLOCKER", "OPEN", 1);

        BugPage page = bugQueryRepository.findPage(
                BugQuery.of(null, "ALL", PROBE_PROJECT_ID, prefix, 1, 20));

        assertEquals(List.of(
                prefix + "-third",
                prefix + "-second",
                prefix + "-first"), page.items().stream().map(item -> item.title()).toList());
    }

    private void insertBug(String title, String severity, String status, int minutesAgo) {
        jdbcTemplate.update("""
                INSERT INTO bug (project_id, source, title, severity, status, found_at,
                                 deleted, created_at, updated_at)
                VALUES (?, 'MANUAL', ?, ?, ?, UTC_TIMESTAMP(3), 0, UTC_TIMESTAMP(3),
                        DATE_SUB(UTC_TIMESTAMP(3), INTERVAL ? MINUTE))
                """, PROBE_PROJECT_ID, title, severity, status, minutesAgo);
    }
}
