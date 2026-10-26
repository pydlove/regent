package com.pydlove.regent.requirement.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequirementTest {

    @Test
    @DisplayName("有任务草稿的需求可以确认并保留依赖")
    void confirmsDecomposition() {
        Requirement requirement = Requirement.create(
                ProjectId.of(7L), "多项目部署", "部署需要支持多个项目", "可保存并筛选", null);
        requirement.applyAnalysis(
                List.of(),
                List.of("允许存量部署继续使用主项目"),
                List.of(
                        draft("T1", List.of()),
                        draft("T2", List.of("T1"))));

        assertEquals(RequirementStatus.READY, requirement.getStatus());
        requirement.confirm();
        assertEquals(RequirementStatus.PLANNED, requirement.getStatus());
        assertEquals(2, requirement.getTaskDrafts().size());
    }

    @Test
    @DisplayName("任务依赖不能形成环")
    void rejectsDependencyCycle() {
        Requirement requirement = Requirement.create(
                ProjectId.of(7L), "循环依赖", "拆解成互相依赖的任务", "能够创建", null);

        assertThrows(DomainException.class, () -> requirement.applyAnalysis(
                List.of(),
                List.of(),
                List.of(
                        draft("T1", List.of("T2")),
                        draft("T2", List.of("T1")))));
    }

    private static RequirementTaskDraft draft(String key, List<String> dependencies) {
        return new RequirementTaskDraft(
                key,
                "任务 " + key,
                "完成 " + key,
                List.of("范围"),
                List.of(),
                List.of("验收通过"),
                dependencies,
                0,
                "L1",
                1);
    }
}
