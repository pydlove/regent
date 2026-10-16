package com.pydlove.regent.project.application.query;

import com.pydlove.regent.project.domain.model.EnvironmentEntry;
import com.pydlove.regent.project.domain.model.EnvironmentScope;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentContextTest {

    @Test
    @DisplayName("项目级同键环境覆盖全局环境")
    void projectOverridesGlobal() {
        EnvironmentEntry global = EnvironmentEntry.create(
                EnvironmentScope.GLOBAL, null, "DB_HOST", "global-db", List.of(), null);
        EnvironmentEntry project = EnvironmentEntry.create(
                EnvironmentScope.PROJECT, ProjectId.of(1L), "DB_HOST", "project-db", List.of(), null);

        EnvironmentContext context = EnvironmentContext.resolve(List.of(project, global));

        assertEquals("project-db", context.values().get("DB_HOST"));
        assertEquals(1, context.variables().size());
        assertEquals("PROJECT", context.variables().get(0).scope());
    }

    @Test
    @DisplayName("指令只列键和说明，不带敏感值")
    void promptDoesNotContainValues() {
        EnvironmentEntry entry = EnvironmentEntry.create(
                EnvironmentScope.GLOBAL, null, "DB_PASSWORD", "super-secret",
                List.of("数据库"), "生产只读账号");

        String prompt = EnvironmentContext.resolve(List.of(entry)).promptSection();

        assertTrue(prompt.contains("`DB_PASSWORD`"), prompt);
        assertTrue(prompt.contains("生产只读账号"), prompt);
        assertFalse(prompt.contains("super-secret"), prompt);
    }

    @Test
    @DisplayName("没有环境配置时不往指令里塞空章节")
    void emptyContextHasNoPromptSection() {
        EnvironmentContext context = EnvironmentContext.resolve(List.of());

        assertTrue(context.isEmpty());
        assertEquals("", context.promptSection());
    }
}
