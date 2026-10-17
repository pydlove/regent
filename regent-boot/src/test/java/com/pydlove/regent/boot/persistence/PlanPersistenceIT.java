package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.plan.domain.model.Plan;
import com.pydlove.regent.plan.domain.model.PlanRun;
import com.pydlove.regent.plan.domain.model.PlanRunStatus;
import com.pydlove.regent.plan.domain.model.PlanTaskSnapshot;
import com.pydlove.regent.plan.domain.model.PlanTaskStatus;
import com.pydlove.regent.plan.domain.model.PlanTrigger;
import com.pydlove.regent.plan.domain.repository.PlanRepository;
import com.pydlove.regent.plan.domain.repository.PlanRunRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 计划聚合与执行结果经真实 MySQL 往返的集成测试。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlanPersistenceIT {

    private static final long PROJECT_ID = 9998L;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PlanRunRepository planRunRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 准备探针项目() {
        jdbcTemplate.update("""
                INSERT INTO project (id, code, name, path, default_decision_level, enabled, deleted, created_at, updated_at)
                VALUES (?, 'plan_it_probe', '计划 IT 探针', '/tmp/plan-it', 'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE name = VALUES(name)
                """, PROJECT_ID);
    }

    @Test
    @DisplayName("计划任务集合与执行总结可以从数据库完整往返")
    void 计划与执行记录往返() {
        Task first = task("计划任务一");
        Task second = task("计划任务二");
        Plan plan = Plan.schedule(
                "持久化计划",
                List.of(first.getId(), second.getId()),
                Instant.now().plusSeconds(3600));
        planRepository.save(plan);

        Plan loadedPlan = planRepository.findById(plan.getId()).orElseThrow();
        assertNotNull(loadedPlan.getId());
        assertEquals(List.of(first.getId(), second.getId()), loadedPlan.getTaskIds());

        PlanRun run = PlanRun.start(
                plan.getId(),
                plan.getName(),
                PlanTrigger.MANUAL,
                List.of(
                        new PlanTaskSnapshot(first.getId(), first.getTitle()),
                        new PlanTaskSnapshot(second.getId(), second.getTitle())));
        planRunRepository.save(run);
        run.recordDispatched(first.getId());
        run.recordDispatched(second.getId());
        run.observe(first.getId(), PlanTaskStatus.SUCCEEDED, "第一项完成");
        run.observe(second.getId(), PlanTaskStatus.FAILED, "第二项失败");
        assertTrue(run.settleIfFinished());
        planRunRepository.save(run);

        PlanRun loadedRun = planRunRepository.findById(run.getId()).orElseThrow();
        assertEquals(PlanRunStatus.PARTIAL, loadedRun.getStatus());
        assertEquals(1, loadedRun.count(PlanTaskStatus.SUCCEEDED));
        assertEquals(1, loadedRun.count(PlanTaskStatus.FAILED));
        assertTrue(loadedRun.getSummary().contains("部分") || loadedRun.getSummary().contains("失败 1"));
    }

    private Task task(String title) {
        Task task = Task.manual(ProjectId.of(PROJECT_ID), title, title, 0);
        taskRepository.save(task);
        return task;
    }
}
