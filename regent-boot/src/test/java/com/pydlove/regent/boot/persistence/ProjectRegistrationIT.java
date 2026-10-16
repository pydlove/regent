package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.execution.application.command.CreateTaskCommand;
import com.pydlove.regent.execution.application.command.TaskCommandService;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskSource;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.project.application.command.ProjectRegistrationService;
import com.pydlove.regent.project.application.command.RegisterProjectCommand;
import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.project.domain.repository.ProjectRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 项目注册与任务录入的集成测试 —— 覆盖「新建任务」这条新链路在真实数据库上的接缝：
 * project 表新增、画像查询、以及 task.project_id 的外键约束。
 *
 * <p>刻意命名为 IT 结尾：需要真实 MySQL（regent_test），surefire 默认不认这个后缀，
 * 所以没有数据库的机器上 mvn verify 依然全绿。手动跑法：
 * {@code mvn -pl regent-boot test -Dtest=ProjectRegistrationIT}
 *
 * <p>每个用例都在事务里执行并回滚，不污染 regent_test（AGENTS.md 数据库铁律第 6 条）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectRegistrationIT {

    @Autowired
    private ProjectRegistrationService projectRegistrationService;

    @Autowired
    private TaskCommandService taskCommandService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectProfileRepository projectProfileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static RegisterProjectCommand command(String code, String path) {
        return new RegisterProjectCommand(code, "IT 注册项目", path, "Java 17 / Maven", "mvn -q test",
                DecisionLevel.L2);
    }

    @Test
    @DisplayName("注册项目：回填主键、画像可查、可选列原样落库")
    void registersProject(@TempDir Path workspace) {
        Project project = projectRegistrationService.register(command("it_reg_full", workspace.toString()));

        assertNotNull(project.getId(), "insert 后必须回填数据库生成的主键");
        assertTrue(projectRepository.existsByCode("it_reg_full"));

        ProjectProfile profile = projectProfileRepository.findById(project.getId()).orElseThrow();
        assertEquals("it_reg_full", profile.code());
        assertEquals(workspace.toString(), profile.path());
        assertEquals(DecisionLevel.L2, profile.defaultDecisionLevel(), "阈值不能在路上被夹成 L1");
        assertTrue(profile.enabled());

        String techStack = jdbcTemplate.queryForObject(
                "SELECT tech_stack FROM project WHERE id = ?", String.class, project.getId().value());
        assertEquals("Java 17 / Maven", techStack, "可空的画像列也要真的写进去");

        List<ProjectProfile> all = projectProfileRepository.findAll();
        assertTrue(all.stream().anyMatch(it -> it.id().equals(project.getId())), "列表接口要能看见它");
    }

    @Test
    @DisplayName("标识撞车被拒：数据库唯一键之前先有一句人能看懂的话")
    void rejectsDuplicatedCode(@TempDir Path workspace) {
        projectRegistrationService.register(command("it_reg_dup", workspace.toString()));

        assertThrows(DomainException.class,
                () -> projectRegistrationService.register(command("it_reg_dup", workspace.toString())));
    }

    @Test
    @DisplayName("路径不存在被拒，且不留下任何行")
    void rejectsMissingPath() {
        assertThrows(DomainException.class,
                () -> projectRegistrationService.register(command("it_reg_missing", "/tmp/regent-it-nowhere-7c1f")));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project WHERE code = 'it_reg_missing'", Integer.class);
        assertEquals(0, rows);
    }

    @Test
    @DisplayName("录入任务：依赖真项目（外键），落库后是待执行 + MANUAL")
    void createsManualTaskForRegisteredProject(@TempDir Path workspace) {
        Project project = projectRegistrationService.register(command("it_reg_task", workspace.toString()));

        Task task = taskCommandService.createManual(new CreateTaskCommand(
                project.getId(), "把 README 改一句", 3));

        assertNotNull(task.getId());
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertEquals(TaskSource.MANUAL, task.getSource());

        Long projectId = jdbcTemplate.queryForObject(
                "SELECT project_id FROM task WHERE id = ?", Long.class, task.getId().value());
        assertEquals(project.getId().value(), projectId);
        assertEquals("MANUAL", jdbcTemplate.queryForObject(
                "SELECT source FROM task WHERE id = ?", String.class, task.getId().value()),
                "来源要存枚举名，界面靠它区分「人录的」与「需求拆的」");
    }

    @Test
    @DisplayName("项目不存在时录入被拒 —— 外键会拦，但报错得是能行动的那句")
    void rejectsTaskForUnknownProject() {
        assertThrows(EntityNotFoundException.class, () -> taskCommandService.createManual(
                new CreateTaskCommand(ProjectId.of(987_654L), "不该落库", 0)));
    }
}
