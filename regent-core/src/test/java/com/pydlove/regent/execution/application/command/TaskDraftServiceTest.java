package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.TaskDraft;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 草稿用例按当前用户隔离，并允许自动保存尚未选项目或尚未写指令的中间状态。 */
class TaskDraftServiceTest {

    private static final UserId USER = UserId.of(11L);

    private final ExecutionFakes.Drafts drafts = new ExecutionFakes.Drafts();
    private final ExecutionFakes.Projects projects = new ExecutionFakes.Projects();
    private final TaskDraftService service = new TaskDraftService(drafts, projects);

    @Test
    @DisplayName("中间状态可以保存，再次保存复用同一份草稿")
    void savesAndUpdatesCurrentDraft() {
        TaskDraft first = service.save(USER,
                new SaveTaskDraftCommand(null, "", 0, List.of()));
        TaskDraft second = service.save(USER,
                new SaveTaskDraftCommand(null, "正在写", 2, List.of(PluginId.of(5L))));

        assertSame(first, second);
        assertEquals(first.getId(), second.getId());
        assertEquals("正在写", service.current(USER).orElseThrow().getPrompt());
        assertEquals(2, drafts.saves);
    }

    @Test
    @DisplayName("选择项目后校验项目存在")
    void validatesSelectedProject() {
        projects.put(ExecutionFakes.Projects.profile(3L, DecisionLevel.L1, true));

        TaskDraft draft = service.save(USER,
                new SaveTaskDraftCommand(ProjectId.of(3L), "任务", 0, List.of()));

        assertEquals(ProjectId.of(3L), draft.getProjectId().orElseThrow());
        assertThrows(EntityNotFoundException.class, () -> service.save(USER,
                new SaveTaskDraftCommand(ProjectId.of(404L), "任务", 0, List.of())));
        assertEquals(1, drafts.saves, "项目校验失败时不能覆盖已有草稿");
    }

    @Test
    @DisplayName("删除只影响当前用户")
    void deletesCurrentUserDraft() {
        service.save(USER, new SaveTaskDraftCommand(null, "我的草稿", 0, List.of()));
        service.save(UserId.of(12L),
                new SaveTaskDraftCommand(null, "另一个人的草稿", 0, List.of()));

        service.delete(USER);

        assertTrue(service.current(USER).isEmpty());
        assertEquals("另一个人的草稿", service.current(UserId.of(12L)).orElseThrow().getPrompt());
    }
}
