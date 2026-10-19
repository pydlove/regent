package com.pydlove.regent.project.application.command;

import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectSummaryStatus;
import com.pydlove.regent.project.domain.repository.ProjectRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectUpdateServiceTest {

    private final InMemoryProjects projects = new InMemoryProjects();
    private final ProjectUpdateService service = new ProjectUpdateService(projects);

    @Test
    @DisplayName("编辑项目时校验新路径并持久化全部可编辑字段")
    void updatesExistingProject(@TempDir Path workspace) throws Exception {
        Project project = projects.create(workspace.toString());
        Path nextWorkspace = Files.createDirectory(workspace.resolve("next"));

        Project updated = service.update(project.getId(), new UpdateProjectCommand(
                "新名称", nextWorkspace.toString(), DecisionLevel.L3, false));

        assertEquals(project, updated);
        assertEquals("新名称", updated.getName());
        assertEquals(nextWorkspace.toString(), updated.getPath());
        assertEquals(DecisionLevel.L3, updated.getDefaultDecisionLevel());
        assertFalse(updated.isEnabled());
        assertEquals(1, projects.detailUpdates);
    }

    @Test
    @DisplayName("新路径不存在时拒绝编辑，且不触达仓储更新")
    void rejectsMissingDirectory(@TempDir Path workspace) {
        Project project = projects.create(workspace.toString());

        assertThrows(DomainException.class, () -> service.update(project.getId(),
                new UpdateProjectCommand(
                        "新名称", "/tmp/regent-missing-" + System.nanoTime(),
                        DecisionLevel.L3, false)));

        assertEquals(0, projects.detailUpdates);
        assertEquals("旧名称", project.getName());
    }

    @Test
    @DisplayName("项目不存在时给出明确异常")
    void rejectsUnknownProject() {
        assertThrows(EntityNotFoundException.class, () -> service.update(ProjectId.of(99L),
                new UpdateProjectCommand("新名称", "/tmp", DecisionLevel.L1, true)));
    }

    private static final class InMemoryProjects implements ProjectRepository {

        private Project project;
        private int detailUpdates;

        private Project create(String path) {
            project = Project.register("app_audit", "旧名称", path, null, null, DecisionLevel.L1);
            project.assignIdentity(ProjectId.of(1L));
            return project;
        }

        @Override
        public boolean existsByCode(String code) {
            return project != null && project.getCode().equals(code);
        }

        @Override
        public void save(Project value) {
            project = value;
        }

        @Override
        public Optional<Project> findById(ProjectId id) {
            return project != null && project.getId().equals(id)
                    ? Optional.of(project)
                    : Optional.empty();
        }

        @Override
        public List<Project> findBySummaryStatus(ProjectSummaryStatus status) {
            return project != null && project.getSummaryStatus() == status
                    ? List.of(project)
                    : List.of();
        }

        @Override
        public void update(Project value) {
            project = value;
        }

        @Override
        public void updateDetails(Project value) {
            project = value;
            detailUpdates++;
        }
    }
}
