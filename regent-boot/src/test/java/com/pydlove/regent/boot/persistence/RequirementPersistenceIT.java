package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskSource;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.Requirement;
import com.pydlove.regent.requirement.domain.model.RequirementSource;
import com.pydlove.regent.requirement.domain.model.RequirementStatus;
import com.pydlove.regent.requirement.domain.model.RequirementTaskDraft;
import com.pydlove.regent.requirement.domain.model.RequirementTrashItem;
import com.pydlove.regent.requirement.domain.repository.RequirementRepository;
import com.pydlove.regent.requirement.domain.repository.TaskDependencyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 需求澄清、任务草稿与任务依赖的真实 MySQL 持久化验证。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RequirementPersistenceIT {

    private static final long PROJECT_ID = 9391L;
    private static final long PROJECT_OWNER_ID = 9390L;
    private static final long TRASH_PROJECT_ID = 9393L;
    private static final long TRASH_USER_ID = 9392L;

    @Autowired
    private RequirementRepository requirementRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskDependencyRepository dependencyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("需求草稿、拆解结果、正式任务和依赖可以完整落库")
    void persistsRequirementDecomposition() {
        jdbcTemplate.update("""
                INSERT INTO app_user
                    (id, username, email, display_name, password_hash,
                     must_change_password, created_at)
                VALUES (?, 'requirement_it_owner', 'requirement-it@example.com',
                        '需求测试负责人', 'x', 0, UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE display_name = VALUES(display_name)
                """, PROJECT_OWNER_ID);
        jdbcTemplate.update("""
                INSERT INTO project
                    (id, owner_id, code, name, path, default_decision_level,
                     enabled, deleted, created_at, updated_at)
                VALUES (?, ?, 'requirement_it', '需求拆解测试', '/tmp/requirement-it',
                        'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE name = VALUES(name), owner_id = VALUES(owner_id), deleted = 0
                """, PROJECT_ID, PROJECT_OWNER_ID);

        Requirement requirement = Requirement.create(
                ProjectId.of(PROJECT_ID),
                null,
                "部署支持多项目",
                "部署和环境都需要归属多个项目",
                "编辑后仍可正确筛选",
                null,
                RequirementSource.IMPORT,
                UserId.of(PROJECT_OWNER_ID));
        requirement.assignNumber(requirementRepository.nextRequirementNo());
        requirementRepository.save(requirement);
        assertNotNull(requirement.getId());
        assertNotNull(requirement.getRequirementNo());

        RequirementTaskDraft first = draft("T1", "扩展部署数据模型", List.of());
        RequirementTaskDraft second = draft("T2", "补充多项目表单", List.of("T1"));
        requirement.applyAnalysis(List.of(), List.of("沿用现有服务器资源"), List.of(first, second));
        requirementRepository.save(requirement);

        Requirement restored = requirementRepository.findById(requirement.getId()).orElseThrow();
        assertEquals(2, restored.getTaskDrafts().size());
        assertEquals(List.of("T1"), restored.getTaskDrafts().get(1).dependencies());
        assertEquals(RequirementSource.IMPORT, restored.getSource());
        assertEquals(UserId.of(PROJECT_OWNER_ID), restored.getCreatedBy());

        Task firstTask = Task.fromRequirement(
                ProjectId.of(PROJECT_ID), requirement.getId(), first.title(), "实现第一个任务", 0);
        Task secondTask = Task.fromRequirement(
                ProjectId.of(PROJECT_ID), requirement.getId(), second.title(), "实现第二个任务", 0);
        taskRepository.save(firstTask);
        taskRepository.save(secondTask);
        dependencyRepository.replace(secondTask.getId(), List.of(firstTask.getId()));

        requirement.confirm();
        requirementRepository.save(requirement);

        List<Task> tasks = taskRepository.findByRequirement(requirement.getId());
        assertEquals(2, tasks.size());
        assertEquals(TaskSource.REQUIREMENT, tasks.get(0).getSource());
        assertEquals(List.of(firstTask.getId()),
                dependencyRepository.findDependencies(secondTask.getId()));
        assertEquals(RequirementStatus.PLANNED,
                requirementRepository.findById(requirement.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("需求删除写入删除人和时间，并从回收站恢复")
    void movesToTrashAndRestores() {
        jdbcTemplate.update("""
                INSERT INTO app_user
                    (id, username, email, display_name, password_hash,
                     must_change_password, created_at)
                VALUES (?, 'requirement_trash_owner', 'requirement-trash@example.com',
                        '回收站测试', 'x', 0, UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE display_name = VALUES(display_name)
                """, TRASH_USER_ID);
        jdbcTemplate.update("""
                INSERT INTO project
                    (id, owner_id, code, name, path, default_decision_level,
                     enabled, deleted, created_at, updated_at)
                VALUES (?, ?, 'requirement_trash_it', '需求回收站测试', '/tmp/requirement-trash-it',
                        'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE name = VALUES(name), owner_id = VALUES(owner_id), deleted = 0
                """, TRASH_PROJECT_ID, TRASH_USER_ID);
        Requirement requirement = Requirement.create(
                ProjectId.of(TRASH_PROJECT_ID),
                null,
                "可删除的需求",
                "该需求用于验证删除和恢复",
                null,
                null,
                RequirementSource.MANUAL,
                UserId.of(TRASH_USER_ID));
        requirement.assignNumber(requirementRepository.nextRequirementNo());
        requirementRepository.save(requirement);
        long requirementNo = requirement.getRequirementNo();
        UserId owner = UserId.of(TRASH_USER_ID);

        requirementRepository.moveToTrash(requirement.getId(), owner, Instant.now());

        assertTrue(requirementRepository.findById(requirement.getId()).isEmpty());
        RequirementTrashItem item = requirementRepository
                .findDeletedByProject(ProjectId.of(TRASH_PROJECT_ID), owner)
                .get(0);
        assertEquals(owner, item.deletedBy());
        assertEquals(owner, item.requirement().getCreatedBy());

        requirementRepository.restore(requirement.getId(), Instant.now());

        assertEquals(requirement.getId(),
                requirementRepository.findById(requirement.getId()).orElseThrow().getId());
        Requirement restoredRequirement = requirementRepository
                .findById(requirement.getId())
                .orElseThrow();
        assertEquals(requirementNo, restoredRequirement.getRequirementNo());
        assertEquals(owner, restoredRequirement.getCreatedBy());
    }

    @Test
    @DisplayName("全局需求编号由独立序列递增发放")
    void allocatesGlobalRequirementNumbers() {
        long first = requirementRepository.nextRequirementNo();
        long second = requirementRepository.nextRequirementNo();

        assertTrue(first > 0);
        assertTrue(second > first);
    }

    private static RequirementTaskDraft draft(String key,
                                              String title,
                                              List<String> dependencies) {
        return new RequirementTaskDraft(
                key,
                title,
                title,
                List.of("相关模块"),
                List.of(),
                List.of("自动化测试通过"),
                dependencies,
                0,
                "L2",
                1);
    }
}
