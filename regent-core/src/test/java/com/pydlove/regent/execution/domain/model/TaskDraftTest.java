package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 草稿必须允许半成品存在，但仍要守住插件列表的结构约束。 */
class TaskDraftTest {

    @Test
    @DisplayName("新草稿允许项目和指令为空，并能保存任意优先级")
    void createsPartialDraft() {
        TaskDraft draft = TaskDraft.create(UserId.of(7L));

        draft.update(null, null, -3, null);

        assertTrue(draft.getProjectId().isEmpty());
        assertEquals("", draft.getPrompt());
        assertEquals(-3, draft.getPriority());
        assertEquals(List.of(), draft.getPluginIds());
    }

    @Test
    @DisplayName("保存时保留完整指令原文并去掉重复插件")
    void preservesPromptAndDeduplicatesPlugins() {
        TaskDraft draft = TaskDraft.create(UserId.of(7L));

        draft.update(ProjectId.of(3L), "  第一行\n正文  ", 5,
                List.of(PluginId.of(9L), PluginId.of(9L), PluginId.of(8L)));

        assertEquals("  第一行\n正文  ", draft.getPrompt());
        assertEquals(List.of(PluginId.of(9L), PluginId.of(8L)), draft.getPluginIds());
    }

    @Test
    @DisplayName("插件为空、超过上限或含空 ID 时拒绝")
    void rejectsInvalidPluginSelection() {
        TaskDraft draft = TaskDraft.create(UserId.of(7L));

        assertThrows(DomainException.class,
                () -> draft.update(null, "任务", 0,
                        Arrays.asList(PluginId.of(1L), null)));
        assertThrows(DomainException.class,
                () -> draft.update(null, "任务", 0,
                        List.of(PluginId.of(1L), PluginId.of(2L), PluginId.of(3L),
                                PluginId.of(4L), PluginId.of(5L), PluginId.of(6L),
                                PluginId.of(7L), PluginId.of(8L), PluginId.of(9L))));
    }
}
