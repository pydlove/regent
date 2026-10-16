package com.pydlove.regent.bug.domain.model;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 缺陷来源与必填字段的领域守卫。 */
class BugTest {

    private static final ProjectId PROJECT = ProjectId.of(1L);

    @Test
    @DisplayName("人工录入：来源为 MANUAL，不绑定来源任务")
    void 人工录入() {
        Bug bug = Bug.reportManually(PROJECT, "导出乱码", BugSeverity.MINOR,
                "导出后打开", "src/Csv.java", "缺 BOM");

        assertEquals(BugSource.MANUAL, bug.getSource());
        assertEquals(BugStatus.OPEN, bug.getStatus());
        assertEquals("导出乱码", bug.getTitle());
        assertEquals(null, bug.getSourceTaskId().orElse(null));
    }

    @Test
    @DisplayName("AI 发现：来源与来源任务必须成对出现")
    void ai发现() {
        Bug bug = Bug.discoveredByAi(PROJECT, null, TaskId.of(9L), "分页错位",
                BugSeverity.MAJOR, "翻页", "src/Page.ts", "offset 算错");

        assertEquals(BugSource.AI_DISCOVERED, bug.getSource());
        assertEquals(TaskId.of(9L), bug.getSourceTaskId().orElseThrow());
    }

    @Test
    @DisplayName("AI 发现缺少来源任务时拒绝落单")
    void ai发现必须带来源任务() {
        assertThrows(DomainException.class, () -> Bug.discoveredByAi(
                PROJECT, null, null, "分页错位", BugSeverity.MAJOR, "", "", ""));
    }

    @Test
    @DisplayName("缺陷标题不能为空")
    void 标题必填() {
        assertThrows(DomainException.class, () -> Bug.reportManually(
                PROJECT, " ", BugSeverity.MINOR, "", "", ""));
    }

    @Test
    @DisplayName("派发修复后进入修复中并持有修复任务 ID")
    void 派发修复任务() {
        Bug bug = Bug.reportManually(PROJECT, "导出乱码", BugSeverity.MINOR,
                "导出后打开", "src/Csv.java", "缺 BOM");

        bug.requestFix(TaskId.of(12L));

        assertEquals(BugStatus.FIXING, bug.getStatus());
        assertEquals(TaskId.of(12L), bug.getFixTaskId().orElseThrow());
    }

    @Test
    @DisplayName("非待处理缺陷不能重复派发修复任务")
    void 重复派发拒绝() {
        Bug bug = Bug.reportManually(PROJECT, "导出乱码", BugSeverity.MINOR,
                null, null, null);
        bug.requestFix(TaskId.of(12L));

        assertThrows(DomainException.class, () -> bug.requestFix(TaskId.of(13L)));
        assertEquals(TaskId.of(12L), bug.getFixTaskId().orElseThrow());
    }

    @Test
    @DisplayName("严重级解析不识别时失败关闭")
    void 严重级失败关闭() {
        assertThrows(DomainException.class, () -> BugSeverity.parse("HIGH"));
    }
}
