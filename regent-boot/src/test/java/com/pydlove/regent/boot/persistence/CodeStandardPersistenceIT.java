package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.execution.application.command.CodeStandardService;
import com.pydlove.regent.execution.application.command.CreateTaskCommand;
import com.pydlove.regent.execution.application.command.SaveCodeStandardCommand;
import com.pydlove.regent.execution.application.command.TaskCodeStandardService;
import com.pydlove.regent.execution.application.command.TaskCommandService;
import com.pydlove.regent.execution.application.command.UploadedDocument;
import com.pydlove.regent.execution.domain.model.CodeStandard;
import com.pydlove.regent.execution.domain.model.CodeStandardCopyStrategy;
import com.pydlove.regent.execution.domain.model.CodeStandardScope;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.command.ProjectRegistrationService;
import com.pydlove.regent.project.application.command.RegisterProjectCommand;
import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 开发规范从文件库、任务快照到跨项目复制的真实数据库链路。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CodeStandardPersistenceIT {

    private static final UserId OWNER = UserId.of(991_000L);

    @Autowired
    private ProjectRegistrationService projectRegistrationService;

    @Autowired
    private ProjectProfileRepository projectProfileRepository;

    @Autowired
    private CodeStandardService codeStandardService;

    @Autowired
    private TaskCodeStandardService taskCodeStandardService;

    @Autowired
    private TaskCommandService taskCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("开发规范可复制到其他项目，并在任务创建时固定版本和注入正文")
    void copiesAndSnapshotsCodeStandards(@TempDir Path workspace) throws Exception {
        jdbcTemplate.update("""
                INSERT INTO app_user (id, username, display_name, password_hash, email, created_at)
                VALUES (?, 'it_code_standard_owner', '规范集成测试', 'x',
                        'it-code-standard@example.com', UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE display_name = VALUES(display_name)
                """, OWNER.value());
        Project sourceProject = projectRegistrationService.register(command(
                "it_code_standard_source", workspace.resolve("source")), OWNER);
        Project targetProject = projectRegistrationService.register(command(
                "it_code_standard_target", workspace.resolve("target")), OWNER);
        ProjectProfile sourceProfile = projectProfileRepository.findById(sourceProject.getId())
                .orElseThrow();

        var source = codeStandardService.create(
                sourceProfile.ownerId(),
                new SaveCodeStandardCommand(
                        sourceProject.getId(),
                        "IT Java 规范",
                        "集成测试规范",
                        CodeStandardScope.BACKEND,
                        true,
                        true,
                        10),
                new UploadedDocument(
                        "java.md",
                        "text/markdown",
                        "# 必须使用 Java 17\n\n测试必须通过。".getBytes(StandardCharsets.UTF_8)));

        var copied = codeStandardService.copy(
                sourceProfile.ownerId(),
                sourceProject.getId(),
                targetProject.getId(),
                List.of(source.standard().id()),
                CodeStandardCopyStrategy.SKIP);
        assertEquals(1, copied.copied().size());
        assertEquals(1, codeStandardService.list(
                sourceProfile.ownerId(), targetProject.getId()).size());

        Task task = taskCommandService.createManual(new CreateTaskCommand(
                sourceProject.getId(),
                "按规范修改后端",
                0,
                List.of(source.standard().id())));
        var snapshots = taskCodeStandardService.listForTask(task.getId(), sourceProfile.ownerId());
        assertEquals(1, snapshots.size());
        assertTrue(taskCodeStandardService.context(task.getId()).promptSection()
                .contains("必须使用 Java 17"));
    }

    private static RegisterProjectCommand command(String code, Path path) throws Exception {
        java.nio.file.Files.createDirectories(path);
        return new RegisterProjectCommand(
                code, code, path.toString(), "Java 17 / Maven", "mvn -q test",
                DecisionLevel.L1);
    }
}
