package com.pydlove.regent.project.application.command;

import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.WorkflowTemplate;
import com.pydlove.regent.project.domain.model.WorkflowTemplateDraft;
import com.pydlove.regent.project.domain.model.WorkflowTemplateId;
import com.pydlove.regent.project.domain.model.WorkflowTemplateStage;
import com.pydlove.regent.project.domain.model.WorkflowTemplateStatus;
import com.pydlove.regent.project.domain.repository.WorkflowTemplateRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.TransactionRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowTemplateCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T06:00:00Z");

    private final MemoryRepository repository = new MemoryRepository();
    private final WorkflowTemplateCommandService service = new WorkflowTemplateCommandService(
            repository,
            new TransactionRunner() {
                @Override
                public <T> T inTransaction(Supplier<T> action) {
                    return action.get();
                }
            },
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("新建模板立即发布，并按列表顺序生成阶段顺序")
    void createsPublishedTemplate() {
        WorkflowTemplate template = service.create(new SaveWorkflowTemplateCommand(
                "敏捷迭代流程",
                "适合双周迭代",
                List.of(
                        stage(null, "需求池"),
                        stage("RELEASE", "发布"))));

        assertEquals(WorkflowTemplateStatus.PUBLISHED, template.status());
        assertEquals(1, template.versionNo());
        assertTrue(template.code().startsWith("workflow_"));
        assertEquals(List.of(1, 2), template.stages().stream()
                .map(WorkflowTemplateStage::order).toList());
        assertTrue(template.stages().get(0).code().startsWith("STAGE_"));
        assertEquals("RELEASE", template.stages().get(1).code());
    }

    @Test
    @DisplayName("复制既有模板会发布下一版本，并把上一发布版本标为已替代")
    void createsNextPublishedVersion() {
        WorkflowTemplate v1 = repository.seed(
                "standard_waterfall", "标准瀑布式", 1,
                WorkflowTemplateStatus.PUBLISHED);

        WorkflowTemplate v2 = service.createVersion(
                v1.id(),
                new SaveWorkflowTemplateCommand(
                        "标准瀑布式",
                        "增加安全评审",
                        List.of(stage("INIT", "立项"), stage("SECURITY", "安全评审"))));

        assertEquals("standard_waterfall", v2.code());
        assertEquals(2, v2.versionNo());
        assertEquals(WorkflowTemplateStatus.PUBLISHED, v2.status());
        assertEquals(
                WorkflowTemplateStatus.SUPERSEDED,
                repository.findById(v1.id()).orElseThrow().status());
        assertEquals(1, repository.findPublished().size());
    }

    @Test
    @DisplayName("已被项目或版本使用的模板不能停用")
    void rejectsArchiveWhenBound() {
        WorkflowTemplate template = repository.seed(
                "flow_a", "流程 A", 1, WorkflowTemplateStatus.PUBLISHED);
        repository.bindings = 1;

        assertThrows(DomainException.class, () -> service.archive(template.id()));
        assertEquals(
                WorkflowTemplateStatus.PUBLISHED,
                repository.findById(template.id()).orElseThrow().status());
    }

    @Test
    @DisplayName("至少保留一个可用于新建项目的发布模板")
    void rejectsArchivingLastPublishedTemplate() {
        WorkflowTemplate template = repository.seed(
                "flow_a", "流程 A", 1, WorkflowTemplateStatus.PUBLISHED);

        assertThrows(DomainException.class, () -> service.archive(template.id()));
    }

    @Test
    @DisplayName("未被使用的非最后一个模板可以停用")
    void archivesUnusedTemplate() {
        WorkflowTemplate first = repository.seed(
                "flow_a", "流程 A", 1, WorkflowTemplateStatus.PUBLISHED);
        repository.seed("flow_b", "流程 B", 1, WorkflowTemplateStatus.PUBLISHED);

        WorkflowTemplate archived = service.archive(first.id());

        assertEquals(WorkflowTemplateStatus.ARCHIVED, archived.status());
        assertEquals(1, repository.findPublished().size());
    }

    private static SaveWorkflowTemplateCommand.Stage stage(String code, String name) {
        return new SaveWorkflowTemplateCommand.Stage(
                code, name, "VERSION", "OWNER", null);
    }

    private static final class MemoryRepository implements WorkflowTemplateRepository {
        private final List<WorkflowTemplate> templates = new ArrayList<>();
        private int nextId = 1;
        private int bindings = 0;

        private WorkflowTemplate seed(
                String code,
                String name,
                int versionNo,
                WorkflowTemplateStatus status) {
            WorkflowTemplate template = new WorkflowTemplate(
                    WorkflowTemplateId.of(nextId++),
                    code,
                    name,
                    versionNo,
                    null,
                    status,
                    List.of(new WorkflowTemplateStage(
                            "INIT", "立项", 1, "VERSION", "OWNER", null)));
            templates.add(template);
            return template;
        }

        @Override
        public List<WorkflowTemplate> findAll() {
            return List.copyOf(templates);
        }

        @Override
        public List<WorkflowTemplate> findPublished() {
            return templates.stream().filter(WorkflowTemplate::selectable).toList();
        }

        @Override
        public Optional<WorkflowTemplate> findById(WorkflowTemplateId id) {
            return templates.stream().filter(item -> item.id().equals(id)).findFirst();
        }

        @Override
        public Optional<WorkflowTemplate> findDefault() {
            return findPublished().stream().findFirst();
        }

        @Override
        public WorkflowTemplate create(WorkflowTemplateDraft draft, Instant createdAt) {
            WorkflowTemplate template = new WorkflowTemplate(
                    WorkflowTemplateId.of(nextId++),
                    draft.code(),
                    draft.name(),
                    draft.versionNo(),
                    draft.description(),
                    WorkflowTemplateStatus.PUBLISHED,
                    draft.stages());
            templates.add(template);
            return template;
        }

        @Override
        public void supersedePublished(String code, Instant updatedAt) {
            templates.replaceAll(template ->
                    template.code().equals(code) && template.selectable()
                            ? template.superseded()
                            : template);
        }

        @Override
        public int nextVersionNo(String code) {
            return templates.stream()
                    .filter(template -> template.code().equals(code))
                    .mapToInt(WorkflowTemplate::versionNo)
                    .max()
                    .orElse(0) + 1;
        }

        @Override
        public int countBindings(WorkflowTemplateId templateId) {
            return bindings;
        }

        @Override
        public void updateStatus(WorkflowTemplate template, Instant updatedAt) {
            templates.replaceAll(item -> item.id().equals(template.id()) ? template : item);
        }

        @Override
        public Optional<WorkflowTemplateId> findProjectTemplate(ProjectId projectId) {
            return Optional.empty();
        }

        @Override
        public Optional<WorkflowTemplateId> findVersionTemplate(ProductVersionId versionId) {
            return Optional.empty();
        }

        @Override
        public void bindProject(
                ProjectId projectId,
                WorkflowTemplateId templateId,
                Instant selectedAt) {
        }

        @Override
        public void bindVersion(
                ProductVersionId versionId,
                WorkflowTemplateId templateId,
                Instant snappedAt) {
        }
    }
}
