package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.command.SaveTaskDraftCommand;
import com.pydlove.regent.execution.application.command.TaskDraftService;
import com.pydlove.regent.execution.domain.model.TaskDraft;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.interfaces.rest.ApiExceptionHandler;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskDraftControllerTest {

    private static final AuthenticatedUser USER =
            new AuthenticatedUser(11L, "alice", "Alice", null, null);

    private RecordingTaskDraftService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = new RecordingTaskDraftService();
        mockMvc = MockMvcBuilders.standaloneSetup(new TaskDraftController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("没有草稿时返回明确空对象")
    void returnsEmptyCurrentDraft() throws Exception {
        mockMvc.perform(get("/api/task-drafts/current")
                        .requestAttr(AuthSessionInterceptor.CURRENT_USER_ATTRIBUTE, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft").doesNotExist());

        assertEquals(UserId.of(USER.id()), service.currentUser);
    }

    @Test
    @DisplayName("自动保存使用当前登录用户，空字段转换为安全默认值")
    void savesCurrentDraft() throws Exception {
        mockMvc.perform(put("/api/task-drafts/current")
                        .requestAttr(AuthSessionInterceptor.CURRENT_USER_ATTRIBUTE, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": 3,
                                  "prompt": "正在写任务",
                                  "priority": 5,
                                  "pluginIds": [8, 9]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(3))
                .andExpect(jsonPath("$.prompt").value("正在写任务"))
                .andExpect(jsonPath("$.pluginIds[0]").value(8));

        assertEquals(UserId.of(USER.id()), service.savedUser);
        assertEquals(ProjectId.of(3L), service.saved.projectId());
        assertEquals(5, service.saved.priority());
        assertEquals(java.util.List.of(PluginId.of(8L), PluginId.of(9L)), service.saved.pluginIds());
    }

    @Test
    @DisplayName("缺少可选项时仍能保存刚打开的空草稿")
    void savesPartialDraftWithDefaults() throws Exception {
        mockMvc.perform(put("/api/task-drafts/current")
                        .requestAttr(AuthSessionInterceptor.CURRENT_USER_ATTRIBUTE, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId": null, "prompt": null}
                                """))
                .andExpect(status().isOk());

        assertNull(service.saved.projectId());
        assertEquals(0, service.saved.priority());
        assertEquals(java.util.List.of(), service.saved.pluginIds());
    }

    @Test
    @DisplayName("删除当前用户草稿返回 204")
    void deletesCurrentDraft() throws Exception {
        mockMvc.perform(delete("/api/task-drafts/current")
                        .requestAttr(AuthSessionInterceptor.CURRENT_USER_ATTRIBUTE, USER))
                .andExpect(status().isNoContent());

        assertEquals(UserId.of(USER.id()), service.deletedUser);
    }

    @Test
    @DisplayName("插件列表里出现空 ID 时按请求错误拒绝")
    void rejectsNullPluginId() throws Exception {
        mockMvc.perform(put("/api/task-drafts/current")
                        .requestAttr(AuthSessionInterceptor.CURRENT_USER_ATTRIBUTE, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt": "任务", "pluginIds": [8, null]}
                                """))
                .andExpect(status().isBadRequest());
    }

    private static final class RecordingTaskDraftService extends TaskDraftService {

        private UserId currentUser;
        private UserId savedUser;
        private UserId deletedUser;
        private SaveTaskDraftCommand saved;

        private RecordingTaskDraftService() {
            super(null, null);
        }

        @Override
        public java.util.Optional<TaskDraft> current(UserId userId) {
            currentUser = userId;
            return java.util.Optional.empty();
        }

        @Override
        public TaskDraft save(UserId userId, SaveTaskDraftCommand command) {
            savedUser = userId;
            saved = command;
            TaskDraft draft = TaskDraft.create(userId);
            draft.update(command.projectId(), command.prompt(), command.priority(), command.pluginIds());
            draft.assignIdentity(com.pydlove.regent.execution.domain.model.TaskDraftId.of(1L));
            return draft;
        }

        @Override
        public void delete(UserId userId) {
            deletedUser = userId;
        }
    }
}
