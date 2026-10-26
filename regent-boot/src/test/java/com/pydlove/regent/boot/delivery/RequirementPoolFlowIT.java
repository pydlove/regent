package com.pydlove.regent.boot.delivery;

import com.pydlove.regent.delivery.application.command.InitiativeCommandService;
import com.pydlove.regent.delivery.application.command.ProductVersionCommandService;
import com.pydlove.regent.delivery.application.command.SaveInitiativeCommand;
import com.pydlove.regent.delivery.domain.model.DeliveryStage;
import com.pydlove.regent.delivery.domain.model.Initiative;
import com.pydlove.regent.delivery.domain.model.ProductVersion;
import com.pydlove.regent.delivery.domain.model.RequirementDelivery;
import com.pydlove.regent.delivery.domain.repository.ProductVersionRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDeliveryRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.command.ProjectRegistrationService;
import com.pydlove.regent.project.application.command.RegisterProjectCommand;
import com.pydlove.regent.project.application.query.WorkflowTemplateQueryService;
import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.requirement.application.command.RequirementCommandService;
import com.pydlove.regent.requirement.application.command.RequirementReviewDecision;
import com.pydlove.regent.requirement.application.command.RequirementReviewRecordService;
import com.pydlove.regent.requirement.application.command.SaveRequirementReviewCommand;
import com.pydlove.regent.requirement.application.command.SaveRequirementCommand;
import com.pydlove.regent.requirement.domain.model.Requirement;
import com.pydlove.regent.requirement.domain.model.RequirementPoolStatus;
import com.pydlove.regent.requirement.domain.model.RequirementReviewStatus;
import com.pydlove.regent.requirement.domain.model.RequirementSource;
import com.pydlove.regent.requirement.domain.repository.RequirementRepository;
import com.pydlove.regent.requirement.domain.repository.RequirementReviewRecordRepository;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 产品版本与需求池从录入到正式设计的真实 MySQL 闭环。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RequirementPoolFlowIT {

    private static final UserId OWNER = UserId.of(990_501L);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProjectRegistrationService projectRegistrationService;

    @Autowired
    private InitiativeCommandService initiativeCommandService;

    @Autowired
    private ProductVersionCommandService productVersionCommandService;

    @Autowired
    private ProductVersionRepository productVersionRepository;

    @Autowired
    private RequirementCommandService requirementCommandService;

    @Autowired
    private RequirementReviewRecordService reviewRecordService;

    @Autowired
    private RequirementRepository requirementRepository;

    @Autowired
    private RequirementReviewRecordRepository reviewRecordRepository;

    @Autowired
    private RequirementDeliveryRepository deliveryRepository;

    @Autowired
    private WorkflowTemplateQueryService workflowTemplateQueryService;

    @BeforeEach
    void 准备账号() {
        jdbcTemplate.update("""
                INSERT INTO app_user
                    (id, username, display_name, email, password_hash, created_at)
                VALUES
                    (?, 'requirement_pool_owner', '需求池测试人',
                     'requirement-pool@example.com', 'x', UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE display_name = VALUES(display_name)
                """, OWNER.value());
    }

    @Test
    @DisplayName("线下需求评审登记后自动创建设计交付")
    void completesRequirementPoolFlow(@TempDir Path workspace) {
        var templates = workflowTemplateQueryService.list();
        assertEquals(1, templates.size());
        assertEquals("standard_waterfall", templates.get(0).code());
        assertEquals("标准瀑布式", templates.get(0).name());
        assertEquals(14, templates.get(0).stages().size());
        Project project = projectRegistrationService.register(new RegisterProjectCommand(
                "requirement_pool_flow_it",
                "需求池闭环测试",
                workspace.toString(),
                null,
                null,
                DecisionLevel.L1), OWNER);
        assertNotNull(workflowTemplateQueryService.projectTemplateId(project.getId()));
        ProductVersion version = productVersionCommandService.create(
                project.getId(), "v1.0.0", "首个版本", "验证完整研发流程", OWNER);
        assertNotNull(workflowTemplateQueryService.versionTemplateId(version.getId()));
        Initiative initiative = initiativeCommandService.create(new SaveInitiativeCommand(
                project.getId(),
                version.getId(),
                "",
                "",
                null,
                null,
                null,
                null,
                null,
                null,
                "会议纪要：同意启动 v1.0.0 版本"), OWNER);
        assertEquals("v1.0.0 版本立项", initiative.getTitle());
        initiativeCommandService.submit(initiative.getId(), OWNER);
        initiativeCommandService.approve(initiative.getId(), OWNER, "同意");

        Requirement requirement = requirementCommandService.create(
                new SaveRequirementCommand(
                        project.getId(),
                        initiative.getId(),
                        "需求池需求",
                        "需求必须先进入产品版本并完成评审",
                        "评审通过后自动生成设计交付",
                        null),
                OWNER);

        requirementCommandService.assignVersion(requirement.getId(), version.getId(), OWNER);
        var submitted = reviewRecordService.submit(
                version.getId(),
                OWNER,
                new SaveRequirementReviewCommand(
                        LocalDate.now(),
                        "确认需求范围、验收标准与交付计划。"),
                List.of());
        assertEquals(RequirementPoolStatus.IN_REVIEW, requirementRepository
                .findById(requirement.getId()).orElseThrow().getPoolStatus());
        assertTrue(deliveryRepository.findActiveByRequirement(requirement.getId()).isEmpty());

        reviewRecordService.decide(
                submitted.record().getId(),
                OWNER,
                RequirementReviewDecision.APPROVE,
                "材料齐全，同意进入设计");

        Requirement stored = requirementRepository.findById(requirement.getId()).orElseThrow();
        assertEquals(RequirementPoolStatus.FORMAL, stored.getPoolStatus());
        assertEquals(OWNER, stored.getCreatedBy());
        assertEquals(RequirementSource.MANUAL, stored.getSource());
        var records = reviewRecordRepository.findByVersion(version.getId());
        assertEquals(1, records.size());
        assertEquals(1, records.get(0).getRequirements().size());
        assertEquals("确认需求范围、验收标准与交付计划。", records.get(0).getMinutes());
        assertEquals(RequirementReviewStatus.APPROVED, records.get(0).getStatus());
        assertEquals("材料齐全，同意进入设计", records.get(0).getReviewComment());
        RequirementDelivery delivery = deliveryRepository
                .findActiveByRequirement(requirement.getId())
                .orElseThrow();
        assertEquals(DeliveryStage.DESIGN, delivery.getStage());

        Requirement draft = requirementCommandService.create(
                new SaveRequirementCommand(
                        project.getId(),
                        initiative.getId(),
                        "批量删除草稿",
                        "验证正式需求和草稿可以一起删除",
                        null,
                        null),
                OWNER);
        assertEquals(2, requirementCommandService.moveManyToTrash(
                List.of(requirement.getId(), draft.getId()), OWNER));
        assertEquals(2, requirementRepository
                .findDeletedByProject(project.getId(), OWNER).size());

        assertEquals(2, requirementCommandService.purgeFromTrash(
                List.of(requirement.getId(), draft.getId()), OWNER));
        assertTrue(requirementRepository
                .findDeletedByProject(project.getId(), OWNER).isEmpty());
        assertTrue(deliveryRepository
                .findActiveByRequirement(requirement.getId()).isEmpty());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM requirement WHERE id IN (?, ?)",
                Integer.class,
                requirement.getId().value(),
                draft.getId().value()));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM requirement_delivery WHERE requirement_id IN (?, ?)",
                Integer.class,
                requirement.getId().value(),
                draft.getId().value()));
    }

    @Test
    @DisplayName("项目负责人可以删除未使用的产品版本")
    void deletesUnusedProductVersion(@TempDir Path workspace) {
        Project project = projectRegistrationService.register(new RegisterProjectCommand(
                "requirement_pool_delete_it",
                "需求池版本删除测试",
                workspace.toString(),
                null,
                null,
                DecisionLevel.L1), OWNER);
        ProductVersion version = productVersionCommandService.create(
                project.getId(), "v0.0.1", "误建版本", "验证未使用版本可删除", OWNER);

        productVersionCommandService.delete(version.getId(), OWNER);

        assertTrue(productVersionRepository.findById(version.getId()).isEmpty());
    }

    @Test
    @DisplayName("项目负责人驳回需求评审后不创建交付，需求可重新提交")
    void rejectsRequirementReview(@TempDir Path workspace) {
        Project project = projectRegistrationService.register(new RegisterProjectCommand(
                "requirement_pool_reject_it",
                "需求评审驳回测试",
                workspace.toString(),
                null,
                null,
                DecisionLevel.L1), OWNER);
        ProductVersion version = productVersionCommandService.create(
                project.getId(), "v1.0.0", "首个版本", "验证需求评审驳回", OWNER);
        Initiative initiative = initiativeCommandService.create(new SaveInitiativeCommand(
                project.getId(),
                version.getId(),
                "",
                "",
                null,
                null,
                null,
                null,
                null,
                null,
                "会议纪要：同意启动 v1.0.0 版本"), OWNER);
        initiativeCommandService.submit(initiative.getId(), OWNER);
        initiativeCommandService.approve(initiative.getId(), OWNER, "同意");
        Requirement requirement = requirementCommandService.create(
                new SaveRequirementCommand(
                        project.getId(),
                        initiative.getId(),
                        "待驳回需求",
                        "验收标准需要补充",
                        null,
                        null),
                OWNER);
        requirementCommandService.assignVersion(requirement.getId(), version.getId(), OWNER);
        var submitted = reviewRecordService.submit(
                version.getId(),
                OWNER,
                new SaveRequirementReviewCommand(LocalDate.now(), "线下评审材料"),
                List.of());

        reviewRecordService.decide(
                submitted.record().getId(),
                OWNER,
                RequirementReviewDecision.REJECT,
                "验收标准不完整");

        assertEquals(RequirementPoolStatus.REJECTED, requirementRepository
                .findById(requirement.getId()).orElseThrow().getPoolStatus());
        assertEquals(RequirementReviewStatus.REJECTED,
                reviewRecordRepository.findById(submitted.record().getId())
                        .orElseThrow().getStatus());
        assertTrue(deliveryRepository.findActiveByRequirement(requirement.getId()).isEmpty());
    }
}
