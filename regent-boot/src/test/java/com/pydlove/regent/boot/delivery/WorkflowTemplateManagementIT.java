package com.pydlove.regent.boot.delivery;

import com.pydlove.regent.project.application.command.SaveWorkflowTemplateCommand;
import com.pydlove.regent.project.application.command.WorkflowTemplateCommandService;
import com.pydlove.regent.project.domain.model.WorkflowTemplate;
import com.pydlove.regent.project.domain.model.WorkflowTemplateStatus;
import com.pydlove.regent.project.domain.repository.WorkflowTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 全局流程模板在真实 MySQL 中的创建、版本替代和停用。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorkflowTemplateManagementIT {

    @Autowired
    private WorkflowTemplateCommandService commandService;

    @Autowired
    private WorkflowTemplateRepository repository;

    @Test
    @DisplayName("模板可创建新版本并停用，历史版本保留")
    void managesGlobalTemplateVersions() {
        WorkflowTemplate v1 = commandService.create(command(
                "双周迭代",
                "需求评审",
                "研发与测试",
                "发布"));

        assertNotNull(v1.id());
        assertEquals(WorkflowTemplateStatus.PUBLISHED, v1.status());
        assertEquals(3, v1.stages().size());

        WorkflowTemplate v2 = commandService.createVersion(v1.id(), command(
                "双周迭代",
                "需求评审",
                "安全评审",
                "研发与测试",
                "发布"));

        assertEquals(v1.code(), v2.code());
        assertEquals(2, v2.versionNo());
        assertEquals(
                WorkflowTemplateStatus.SUPERSEDED,
                repository.findById(v1.id()).orElseThrow().status());
        assertTrue(repository.findPublished().stream()
                .anyMatch(template -> template.id().equals(v2.id())));

        WorkflowTemplate archived = commandService.archive(v2.id());

        assertEquals(WorkflowTemplateStatus.ARCHIVED, archived.status());
        assertEquals(
                WorkflowTemplateStatus.SUPERSEDED,
                repository.findById(v1.id()).orElseThrow().status());
    }

    private static SaveWorkflowTemplateCommand command(String name, String... stages) {
        return new SaveWorkflowTemplateCommand(
                name,
                "真实数据库集成测试",
                List.of(stages).stream()
                        .map(stage -> new SaveWorkflowTemplateCommand.Stage(
                                null, stage, "VERSION", "OWNER", null))
                        .toList());
    }
}
