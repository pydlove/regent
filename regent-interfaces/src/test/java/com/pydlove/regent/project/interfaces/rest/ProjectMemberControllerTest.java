package com.pydlove.regent.project.interfaces.rest;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.command.ProjectMemberCommandService;
import com.pydlove.regent.project.application.command.ProjectMemberRoleAssignment;
import com.pydlove.regent.project.application.query.ProjectMemberQueryService;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.support.TestCurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectMemberControllerTest {

    private ProjectMemberCommandService commandService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        commandService = mock(ProjectMemberCommandService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ProjectMemberController(
                                commandService,
                                mock(ProjectMemberQueryService.class)))
                .addInterceptors(TestCurrentUser.interceptor())
                .build();
    }

    @Test
    @DisplayName("批量添加团队成员时统一传递成员邮箱和项目角色")
    void batchAddsMembersWithOneRole() throws Exception {
        mockMvc.perform(post("/api/projects/9/members/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "members": [
                                    {"email": "alice@example.com", "roles": ["QA", "OPS"]},
                                    {"email": "bob@example.com", "roles": ["DEVELOPER"]}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        verify(commandService).addAssignments(
                eq(ProjectId.of(9L)),
                eq(List.of(
                        new ProjectMemberRoleAssignment(
                                "alice@example.com",
                                List.of(ProjectMemberRole.QA, ProjectMemberRole.OPS)),
                        new ProjectMemberRoleAssignment(
                                "bob@example.com",
                                List.of(ProjectMemberRole.DEVELOPER)))),
                eq(UserId.of(TestCurrentUser.USER_ID)));
    }
}
