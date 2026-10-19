package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.project.application.command.EnvironmentEntryService;
import com.pydlove.regent.project.application.command.ProjectRegistrationService;
import com.pydlove.regent.project.application.command.RegisterProjectCommand;
import com.pydlove.regent.project.application.command.SaveEnvironmentCommand;
import com.pydlove.regent.project.application.query.EnvironmentContext;
import com.pydlove.regent.project.domain.model.EnvironmentEntry;
import com.pydlove.regent.project.domain.model.EnvironmentScope;
import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.repository.EnvironmentEntryRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 环境信息的真实 MySQL 持久化验证。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnvironmentPersistenceIT {

    @Autowired
    private ProjectRegistrationService projectRegistrationService;

    @Autowired
    private EnvironmentEntryService environmentEntryService;

    @Autowired
    private EnvironmentEntryRepository environmentEntryRepository;

    @Test
    @DisplayName("历史显式环境键落库后，项目级同键仍覆盖全局")
    void persistsAndResolvesProjectOverride(@TempDir Path workspace) {
        Project project = projectRegistrationService.register(new RegisterProjectCommand(
                "it_env_project", "环境集成项目", workspace.toString(), null, null, DecisionLevel.L1));

        environmentEntryRepository.save(EnvironmentEntry.create(
                EnvironmentScope.GLOBAL, null, "IT_DB_HOST", "global-db",
                List.of("数据库"), "全局数据库"));
        environmentEntryRepository.save(EnvironmentEntry.create(
                EnvironmentScope.PROJECT, project.getId(), "IT_DB_HOST", "project-db",
                List.of("数据库"), "项目数据库"));
        environmentEntryRepository.save(EnvironmentEntry.create(
                EnvironmentScope.PROJECT, project.getId(), "IT_DB_TOKEN", "secret",
                List.of("凭据"), "项目凭据"));

        List<EnvironmentEntry> applicable = environmentEntryRepository.findApplicableTo(project.getId());
        EnvironmentContext context = EnvironmentContext.resolve(applicable);

        assertEquals(2, context.variables().size());
        assertEquals("project-db", context.values().get("IT_DB_HOST"));
        assertEquals("secret", context.values().get("IT_DB_TOKEN"));
        assertTrue(context.promptSection().contains("`IT_DB_HOST`"));
    }

    @Test
    @DisplayName("菜单创建环境信息时自动生成唯一键，名称必填")
    void generatesKeysAndRequiresName(@TempDir Path workspace) {
        Project project = projectRegistrationService.register(new RegisterProjectCommand(
                "it_env_auto_project", "自动环境项目", workspace.toString(), null, null, DecisionLevel.L1));

        EnvironmentEntry global = environmentEntryService.create(new SaveEnvironmentCommand(
                EnvironmentScope.GLOBAL, null, "global-db", List.of("数据库"),
                "全局数据库只读连接串", false));
        EnvironmentEntry scoped = environmentEntryService.create(new SaveEnvironmentCommand(
                EnvironmentScope.PROJECT, project.getId(), "project-db", List.of(),
                "项目数据库连接串", true));

        assertTrue(global.getKey().startsWith("ENV_"), global.getKey());
        assertTrue(scoped.getKey().startsWith("ENV_"), scoped.getKey());
        assertNotEquals(global.getKey(), scoped.getKey());
        assertEquals("全局数据库只读连接串", global.getDescription());
        assertTrue(scoped.isSecret());
        assertThrows(DomainException.class, () -> environmentEntryService.create(
                new SaveEnvironmentCommand(
                        EnvironmentScope.GLOBAL, null, "x", List.of(), "  ", false)));
    }
}
