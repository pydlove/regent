package com.pydlove.regent.boot.delivery;

import com.pydlove.regent.delivery.application.command.InitiativeAttachmentService;
import com.pydlove.regent.delivery.application.command.InitiativeCommandService;
import com.pydlove.regent.delivery.application.command.SaveInitiativeCommand;
import com.pydlove.regent.delivery.application.command.UploadedInitiativeAttachment;
import com.pydlove.regent.delivery.domain.model.Initiative;
import com.pydlove.regent.delivery.domain.model.InitiativeStatus;
import com.pydlove.regent.delivery.domain.repository.InitiativeAttachmentRepository;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.command.ProjectRegistrationService;
import com.pydlove.regent.project.application.command.RegisterProjectCommand;
import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.shared.domain.DomainException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InitiativeMeetingMinutesIT {

    private static final UserId OWNER = UserId.of(990_001L);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProjectRegistrationService projectRegistrationService;

    @Autowired
    private InitiativeCommandService initiativeCommandService;

    @Autowired
    private InitiativeAttachmentService attachmentService;

    @Autowired
    private InitiativeRepository initiativeRepository;

    @Autowired
    private InitiativeAttachmentRepository attachmentRepository;

    @BeforeEach
    void 准备账号() {
        jdbcTemplate.update("""
                INSERT INTO app_user
                    (id, username, display_name, email, password_hash, created_at)
                VALUES
                    (?, 'initiative_minutes_owner', '立项审批人',
                     'initiative-minutes@example.com', 'x', UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE display_name = VALUES(display_name)
                """, OWNER.value());
    }

    @Test
    @DisplayName("上传会议纪要附件后可以无正文同意立项，并持久化回读")
    void approvesWithUploadedMinutes(@TempDir Path workspace) {
        Project project = projectRegistrationService.register(new RegisterProjectCommand(
                "initiative_minutes_it",
                "立项会议纪要测试",
                workspace.toString(),
                null,
                null,
                DecisionLevel.L1), OWNER);
        Initiative initiative = initiativeCommandService.create(new SaveInitiativeCommand(
                project.getId(),
                "审批材料闭环",
                "验证会议纪要附件",
                null,
                null,
                null), OWNER);
        initiativeCommandService.submit(initiative.getId(), OWNER);

        assertThrows(DomainException.class, () -> initiativeCommandService.approve(
                initiative.getId(), OWNER, "同意", ""));

        attachmentService.attach(
                initiative.getId(),
                OWNER,
                new UploadedInitiativeAttachment(
                        "立项会议纪要.pdf",
                        "application/pdf",
                        "%PDF-1.4\n会议结论：同意立项".getBytes()));

        initiativeCommandService.approve(initiative.getId(), OWNER, "同意", "");

        Initiative stored = initiativeRepository.findById(initiative.getId()).orElseThrow();
        assertEquals(InitiativeStatus.APPROVED, stored.getStatus());
        assertFalse(attachmentRepository.findByInitiative(initiative.getId()).isEmpty());
    }
}
