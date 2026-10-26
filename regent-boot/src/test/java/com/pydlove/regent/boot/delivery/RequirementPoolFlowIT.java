package com.pydlove.regent.boot.delivery;

import com.pydlove.regent.delivery.application.command.InitiativeCommandService;
import com.pydlove.regent.delivery.application.command.ProductVersionCommandService;
import com.pydlove.regent.delivery.application.command.SaveInitiativeCommand;
import com.pydlove.regent.delivery.domain.model.DeliveryStage;
import com.pydlove.regent.delivery.domain.model.Initiative;
import com.pydlove.regent.delivery.domain.model.ProductVersion;
import com.pydlove.regent.delivery.domain.model.ProductVersionStatus;
import com.pydlove.regent.delivery.domain.model.RequirementDelivery;
import com.pydlove.regent.delivery.domain.repository.ProductVersionRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDeliveryRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.command.ProjectRegistrationService;
import com.pydlove.regent.project.application.command.RegisterProjectCommand;
import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.requirement.application.command.RequirementCommandService;
import com.pydlove.regent.requirement.application.command.SaveRequirementCommand;
import com.pydlove.regent.requirement.domain.model.Requirement;
import com.pydlove.regent.requirement.domain.model.RequirementPoolStatus;
import com.pydlove.regent.requirement.domain.repository.RequirementRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    private RequirementCommandService requirementCommandService;

    @Autowired
    private ProductVersionRepository productVersionRepository;

    @Autowired
    private RequirementRepository requirementRepository;

    @Autowired
    private RequirementDeliveryRepository deliveryRepository;

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
    @DisplayName("版本确认后需求评审通过并自动创建设计交付")
    void completesRequirementPoolFlow(@TempDir Path workspace) {
        Project project = projectRegistrationService.register(new RegisterProjectCommand(
                "requirement_pool_flow_it",
                "需求池闭环测试",
                workspace.toString(),
                null,
                null,
                DecisionLevel.L1), OWNER);
        ProductVersion version = productVersionCommandService.create(
                project.getId(), "v1.0.0", "首个版本", "验证完整研发流程", OWNER);
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
        initiativeCommandService.approve(
                initiative.getId(), OWNER, "同意", "");

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
        requirementCommandService.submitPoolReview(requirement.getId(), OWNER);
        productVersionCommandService.confirm(version.getId(), OWNER);
        requirementCommandService.approvePoolReview(requirement.getId(), OWNER, "同意进入设计");

        Requirement stored = requirementRepository.findById(requirement.getId()).orElseThrow();
        assertEquals(RequirementPoolStatus.FORMAL, stored.getPoolStatus());
        assertEquals(
                ProductVersionStatus.CONFIRMED,
                productVersionRepository.findById(version.getId()).orElseThrow().getStatus());
        RequirementDelivery delivery = deliveryRepository
                .findActiveByRequirement(requirement.getId())
                .orElseThrow();
        assertEquals(DeliveryStage.DESIGN, delivery.getStage());
    }
}
