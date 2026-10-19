package com.pydlove.regent.project.interfaces.rest;

import com.pydlove.regent.project.application.command.ProjectUpdateService;
import com.pydlove.regent.project.application.command.UpdateProjectCommand;
import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectSummaryStatus;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.support.TestCurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectControllerTest {

    private RecordingProjectUpdateService updateService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Project project = Project.register(
                "app_audit", "审计平台", "/tmp/app-audit", null, null, DecisionLevel.L1);
        project.assignIdentity(ProjectId.of(7L));
        project.updateDetails("安全审计平台", "/tmp/app-audit-next", DecisionLevel.L3, false);
        updateService = new RecordingProjectUpdateService(project);

        ProjectProfileRepository profiles = new ProjectProfileRepository() {
            @Override
            public Optional<ProjectProfile> findById(ProjectId id) {
                return Optional.of(new ProjectProfile(
                        project.getId(), project.getCode(), project.getName(), project.getPath(),
                        project.getDefaultDecisionLevel(), project.isEnabled(),
                        ProjectSummaryStatus.NOT_GENERATED, null, null, null));
            }

            @Override
            public Optional<ProjectProfile> findByCode(String code) {
                return Optional.empty();
            }

            @Override
            public List<ProjectProfile> findAll() {
                return List.of();
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(new ProjectController(
                profiles, null, updateService, null, null, null))
                .addInterceptors(TestCurrentUser.interceptor())
                .build();
    }

    @Test
    @DisplayName("编辑项目走 PUT 接口并映射全部可编辑字段")
    void updatesProjectThroughPutEndpoint() throws Exception {
        mockMvc.perform(put("/api/projects/{id}", 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"安全审计平台","path":"/tmp/app-audit-next",
                                 "defaultDecisionLevel":"L3","enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("安全审计平台"))
                .andExpect(jsonPath("$.path").value("/tmp/app-audit-next"))
                .andExpect(jsonPath("$.defaultDecisionLevel").value("L3"))
                .andExpect(jsonPath("$.enabled").value(false));

        assertEquals(ProjectId.of(7L), updateService.id);
        assertEquals("安全审计平台", updateService.command.name());
        assertEquals(DecisionLevel.L3, updateService.command.defaultDecisionLevel());
        assertFalse(updateService.command.enabled());
    }

    private static final class RecordingProjectUpdateService extends ProjectUpdateService {

        private final Project project;
        private ProjectId id;
        private UpdateProjectCommand command;

        private RecordingProjectUpdateService(Project project) {
            super(null);
            this.project = project;
        }

        @Override
        public Project update(ProjectId id, UpdateProjectCommand command) {
            this.id = id;
            this.command = command;
            return project;
        }

        @Override
        public Project update(ProjectId id, UpdateProjectCommand command, UserId ownerId) {
            return update(id, command);
        }
    }
}
