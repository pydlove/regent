package com.pydlove.regent.boot.delivery;

import com.pydlove.regent.delivery.application.command.InitiativeCommandService;
import com.pydlove.regent.delivery.application.command.InitiativePlanCommandService;
import com.pydlove.regent.delivery.application.command.ProductVersionCommandService;
import com.pydlove.regent.delivery.application.command.RequirementDeliveryAction;
import com.pydlove.regent.delivery.application.command.RequirementDeliveryCommandService;
import com.pydlove.regent.delivery.application.command.RequirementDesignCommandService;
import com.pydlove.regent.delivery.application.command.RequirementDesignAttachmentService;
import com.pydlove.regent.delivery.application.command.SaveDesignModuleCommand;
import com.pydlove.regent.delivery.application.command.SaveInitiativeCommand;
import com.pydlove.regent.delivery.application.command.SaveInitiativePlanCommand;
import com.pydlove.regent.delivery.application.command.UploadedRequirementDesignAttachment;
import com.pydlove.regent.delivery.domain.model.DeliveryStage;
import com.pydlove.regent.delivery.domain.model.DesignModuleStatus;
import com.pydlove.regent.delivery.domain.model.Initiative;
import com.pydlove.regent.delivery.domain.model.ProductVersion;
import com.pydlove.regent.delivery.domain.model.RequirementDelivery;
import com.pydlove.regent.delivery.domain.model.RequirementDesignModule;
import com.pydlove.regent.delivery.domain.repository.RequirementDeliveryRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDesignModuleRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDesignAttachmentRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.command.ProjectRegistrationService;
import com.pydlove.regent.project.application.command.RegisterProjectCommand;
import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectMemberRepository;
import com.pydlove.regent.requirement.application.command.RequirementCommandService;
import com.pydlove.regent.requirement.application.command.RequirementReviewDecision;
import com.pydlove.regent.requirement.application.command.RequirementReviewRecordService;
import com.pydlove.regent.requirement.application.command.SaveRequirementCommand;
import com.pydlove.regent.requirement.application.command.SaveRequirementReviewCommand;
import com.pydlove.regent.requirement.domain.model.Requirement;
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

/** 概要设计、模块派工、研发详细设计和设计评审的真实 MySQL 闭环。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ModularDesignFlowIT {

    private static final UserId OWNER = UserId.of(990_701L);

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ProjectRegistrationService projectRegistrationService;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private ProductVersionCommandService productVersionCommandService;
    @Autowired
    private InitiativeCommandService initiativeCommandService;
    @Autowired
    private InitiativePlanCommandService initiativePlanCommandService;
    @Autowired
    private RequirementCommandService requirementCommandService;
    @Autowired
    private RequirementReviewRecordService reviewRecordService;
    @Autowired
    private RequirementDeliveryCommandService deliveryCommandService;
    @Autowired
    private RequirementDesignCommandService designCommandService;
    @Autowired
    private RequirementDesignAttachmentService designAttachmentService;
    @Autowired
    private RequirementDeliveryRepository deliveryRepository;
    @Autowired
    private RequirementDesignModuleRepository moduleRepository;
    @Autowired
    private RequirementDesignAttachmentRepository designAttachmentRepository;

    @BeforeEach
    void 准备账号() {
        jdbcTemplate.update("""
                INSERT INTO app_user
                    (id, username, display_name, email, password_hash, created_at)
                VALUES
                    (?, 'modular_design_owner', '模块设计测试人',
                     'modular-design@example.com', 'x', UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE display_name = VALUES(display_name)
                """, OWNER.value());
    }

    @Test
    @DisplayName("需求评审后依次完成概要设计、模块派工、研发设计和设计评审")
    void completesModularDesignFlow(@TempDir Path workspace) {
        Project project = projectRegistrationService.register(new RegisterProjectCommand(
                "modular_design_it",
                "模块设计闭环",
                workspace.toString(),
                null,
                null,
                DecisionLevel.L1), OWNER);
        projectMemberRepository.ensure(
                project.getId(), OWNER, ProjectMemberRole.DEVELOPER);

        ProductVersion version = productVersionCommandService.create(
                project.getId(), "v1.0.0", "模块设计版本", "验证派工与设计评审", OWNER);
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
                "会议纪要：同意启动版本"), OWNER);
        initiativePlanCommandService.save(
                initiative.getId(),
                new SaveInitiativePlanCommand(
                        LocalDate.now(),
                        LocalDate.now().plusDays(10),
                        "设计与研发闭环",
                        List.of(new SaveInitiativePlanCommand.Item(
                                "概要与模块设计",
                                "TECH_LEAD",
                                LocalDate.now(),
                                LocalDate.now().plusDays(4),
                                1))),
                OWNER);
        initiativeCommandService.submit(initiative.getId(), OWNER);
        initiativeCommandService.approve(initiative.getId(), OWNER, "同意");

        Requirement requirement = requirementCommandService.create(
                new SaveRequirementCommand(
                        project.getId(),
                        initiative.getId(),
                        "模块化设计需求",
                        "验证研发各自提交模块设计",
                        "每个模块有负责人和设计文档",
                        null),
                OWNER);
        requirementCommandService.assignVersion(requirement.getId(), version.getId(), OWNER);
        var review = reviewRecordService.submit(
                version.getId(),
                OWNER,
                new SaveRequirementReviewCommand(LocalDate.now(), "线下评审通过"),
                List.of());
        reviewRecordService.decide(
                review.record().getId(),
                OWNER,
                RequirementReviewDecision.APPROVE,
                "同意进入设计");

        RequirementDelivery delivery = deliveryRepository
                .findActiveByRequirement(requirement.getId())
                .orElseThrow();
        assertEquals(DeliveryStage.OUTLINE_DESIGN, delivery.getStage());

        designAttachmentService.uploadOutline(
                delivery.getId(),
                OWNER,
                new UploadedRequirementDesignAttachment(
                        "概要设计.md",
                        "text/markdown",
                        "# 概要设计\n定义权限、交付和需求模块".getBytes()));
        deliveryCommandService.act(
                delivery.getId(),
                RequirementDeliveryAction.SUBMIT_OUTLINE_DESIGN,
                "概要设计文档已上传",
                null,
                null,
                OWNER);
        delivery = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertEquals(DeliveryStage.DESIGN, delivery.getStage());

        RequirementDesignModule module = designCommandService.create(
                delivery.getId(),
                new SaveDesignModuleCommand(
                        "权限模块",
                        "负责角色、数据隔离和审计",
                        OWNER),
                OWNER);
        designAttachmentService.uploadModule(
                delivery.getId(),
                module.getId(),
                OWNER,
                new UploadedRequirementDesignAttachment(
                        "权限模块详细设计.md",
                        "text/markdown",
                        "# 权限模块\n角色校验、数据隔离与审计".getBytes()));
        module = designCommandService.submitModule(
                delivery.getId(), module.getId(), OWNER);
        assertEquals(DesignModuleStatus.SUBMITTED, module.getStatus());
        assertEquals(1, moduleRepository.findByDelivery(delivery.getId()).size());
        assertEquals(1, designAttachmentRepository.findByModule(module.getId()).size());

        deliveryCommandService.act(
                delivery.getId(),
                RequirementDeliveryAction.SUBMIT_DESIGN,
                "模块设计齐全",
                null,
                null,
                OWNER);
        delivery = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertEquals(DeliveryStage.DESIGN_REVIEW, delivery.getStage());

        deliveryCommandService.act(
                delivery.getId(),
                RequirementDeliveryAction.APPROVE_DESIGN,
                "设计通过",
                null,
                null,
                OWNER);
        delivery = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertEquals(DeliveryStage.DEVELOPMENT, delivery.getStage());
    }
}
