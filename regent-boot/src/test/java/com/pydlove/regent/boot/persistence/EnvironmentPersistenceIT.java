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
    @DisplayName("环境信息落库后可合并查询，项目级同键覆盖全局")
    void persistsAndResolvesProjectOverride(@TempDir Path workspace) {
        Project project = projectRegistrationService.register(new RegisterProjectCommand(
                "it_env_project", "环境集成项目", workspace.toString(), null, null, DecisionLevel.L1));

        environmentEntryService.create(new SaveEnvironmentCommand(
                EnvironmentScope.GLOBAL, null, "IT_DB_HOST", "global-db",
                List.of("数据库"), "全局数据库"));
        environmentEntryService.create(new SaveEnvironmentCommand(
                EnvironmentScope.PROJECT, project.getId(), "IT_DB_HOST", "project-db",
                List.of("数据库"), "项目数据库"));
        environmentEntryService.create(new SaveEnvironmentCommand(
                EnvironmentScope.PROJECT, project.getId(), "IT_DB_TOKEN", "secret",
                List.of("凭据"), null));

        List<EnvironmentEntry> applicable = environmentEntryRepository.findApplicableTo(project.getId());
        EnvironmentContext context = EnvironmentContext.resolve(applicable);

        assertEquals(2, context.variables().size());
        assertEquals("project-db", context.values().get("IT_DB_HOST"));
        assertEquals("secret", context.values().get("IT_DB_TOKEN"));
        assertTrue(context.promptSection().contains("`IT_DB_HOST`"));
    }

    @Test
    @DisplayName("同一归属内重复键被业务规则拒绝")
    void rejectsDuplicateKeyInSameScope() {
        EnvironmentEntry first = environmentEntryService.create(new SaveEnvironmentCommand(
                EnvironmentScope.GLOBAL, null, "IT_UNIQUE_KEY", "one", List.of(), null));

        assertThrows(DomainException.class, () -> environmentEntryService.create(
                new SaveEnvironmentCommand(
                        EnvironmentScope.GLOBAL, null, "IT_UNIQUE_KEY", "two", List.of(), null)));

        environmentEntryService.delete(first.getId());
        environmentEntryService.create(new SaveEnvironmentCommand(
                EnvironmentScope.GLOBAL, null, "IT_UNIQUE_KEY", "three", List.of(), null));
    }
}
