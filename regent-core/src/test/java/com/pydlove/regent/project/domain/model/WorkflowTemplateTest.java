package com.pydlove.regent.project.domain.model;

import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowTemplateTest {

    private static WorkflowTemplateStage stage(String code, int order) {
        return new WorkflowTemplateStage(
                code, "阶段 " + order, order, "VERSION", "OWNER", null);
    }

    @Test
    @DisplayName("模板版本必须至少包含一个阶段，阶段编码和顺序不能重复")
    void validatesStages() {
        assertThrows(DomainException.class, () -> new WorkflowTemplateDraft(
                "flow_a", "流程 A", 1, null, List.of()));
        assertThrows(DomainException.class, () -> new WorkflowTemplateDraft(
                "flow_a", "流程 A", 1, null,
                List.of(stage("SAME", 1), stage("SAME", 2))));
        assertThrows(DomainException.class, () -> new WorkflowTemplateDraft(
                "flow_a", "流程 A", 1, null,
                List.of(stage("A", 1), stage("B", 1))));
    }

    @Test
    @DisplayName("阶段范围与审批角色必须是系统已知值")
    void validatesScopeAndApproverRole() {
        assertThrows(DomainException.class, () -> new WorkflowTemplateStage(
                "A", "阶段 A", 1, "PROJECT", "OWNER", null));
        assertThrows(DomainException.class, () -> new WorkflowTemplateStage(
                "A", "阶段 A", 1, "VERSION", "UNKNOWN", null));
    }

    @Test
    @DisplayName("发布版本可以替代或停用，状态不能隐式改变")
    void changesStatusThroughDomainMethods() {
        WorkflowTemplate template = new WorkflowTemplate(
                WorkflowTemplateId.of(1L),
                "standard_waterfall",
                "标准瀑布式",
                1,
                null,
                WorkflowTemplateStatus.PUBLISHED,
                List.of(stage("A", 1)));

        WorkflowTemplate superseded = template.superseded();
        WorkflowTemplate archived = superseded.archived();

        assertTrue(template.selectable());
        assertFalse(superseded.selectable());
        assertEquals(WorkflowTemplateStatus.SUPERSEDED, superseded.status());
        assertEquals(WorkflowTemplateStatus.ARCHIVED, archived.status());
        assertThrows(DomainException.class, superseded::superseded);
    }
}
