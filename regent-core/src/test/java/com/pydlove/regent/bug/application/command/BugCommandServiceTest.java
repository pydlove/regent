package com.pydlove.regent.bug.application.command;

import com.pydlove.regent.bug.domain.model.Bug;
import com.pydlove.regent.bug.domain.model.BugSeverity;
import com.pydlove.regent.bug.domain.model.BugStatus;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 缺陷写侧用例的落库编排。 */
class BugCommandServiceTest {

    private static final ProjectId PROJECT = ProjectId.of(1L);

    private final ExecutionFakes.Bugs bugs = new ExecutionFakes.Bugs();
    private final BugCommandService service = new BugCommandService(
            bugs, new ExecutionFakes.Projects());

    private Bug bug;

    @BeforeEach
    void setUp() {
        bug = Bug.reportManually(PROJECT, "导出文件名乱码", BugSeverity.MAJOR,
                null, null, null);
        bugs.save(bug);
        bug.requestFix(TaskId.of(12L));
        bugs.save(bug);
    }

    @Test
    @DisplayName("标记已修复与验证关闭都会保存领域状态")
    void 状态迁移落库() {
        service.markFixed(bug.getId());
        assertEquals(BugStatus.FIXED, bugs.findById(bug.getId()).orElseThrow().getStatus());

        service.verify(bug.getId());
        assertEquals(BugStatus.CLOSED, bugs.findById(bug.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("不修复理由随状态一起落库")
    void 不修复落库() {
        service.wontFix(bug.getId(), "上游接口决定保留该行为");

        Bug reloaded = bugs.findById(bug.getId()).orElseThrow();
        assertEquals(BugStatus.WONTFIX, reloaded.getStatus());
        assertEquals("上游接口决定保留该行为", reloaded.getWontFixReason().orElseThrow());
    }

    @Test
    @DisplayName("不修复理由为空时仍会保存终态")
    void 不修复无理由落库() {
        service.wontFix(bug.getId(), null);

        Bug reloaded = bugs.findById(bug.getId()).orElseThrow();
        assertEquals(BugStatus.WONTFIX, reloaded.getStatus());
        assertNull(reloaded.getWontFixReason().orElse(null));
    }
}
