package com.pydlove.regent.project.application.command;

import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectSummaryStatus;
import com.pydlove.regent.project.domain.port.ProjectSummaryGenerator;
import com.pydlove.regent.project.domain.repository.ProjectRepository;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSummaryGenerationServiceTest {

    private final InMemoryProjects projects = new InMemoryProjects();
    private final Project project = projects.create("regent", "/tmp/regent");

    @Test
    @DisplayName("生成成功：只读上下文交给生成器，资料与时间写回项目")
    void storesGeneratedSummary() {
        StringBuilder promptContext = new StringBuilder();
        ProjectSummaryGenerator generator = request -> {
            promptContext.append(request.code()).append('|').append(request.path());
            return "# Regent\n\nJava 17 / Maven";
        };

        service(generator).schedule(project.getId());

        Project stored = projects.findById(project.getId()).orElseThrow();
        assertEquals(ProjectSummaryStatus.READY, stored.getSummaryStatus());
        assertEquals("# Regent\n\nJava 17 / Maven", stored.getSummary());
        assertTrue(stored.getSummaryGeneratedAt() != null);
        assertEquals("regent|/tmp/regent", promptContext.toString());
    }

    @Test
    @DisplayName("生成异常：状态收敛为失败，原因可展示，不留永久生成中")
    void storesGenerationFailure() {
        ProjectSummaryGenerator generator = request -> {
            throw new IllegalStateException("codex 不可用");
        };

        service(generator).schedule(project.getId());

        Project stored = projects.findById(project.getId()).orElseThrow();
        assertEquals(ProjectSummaryStatus.FAILED, stored.getSummaryStatus());
        assertTrue(stored.getSummaryError().contains("codex 不可用"), stored.getSummaryError());
    }

    @Test
    @DisplayName("启动恢复把上一进程遗留的生成中状态转成可重试失败")
    void recoversInterruptedGeneration() {
        project.startSummaryGeneration();
        projects.update(project);

        int recovered = service(request -> "不应被调用").recoverInterrupted();

        Project stored = projects.findById(project.getId()).orElseThrow();
        assertEquals(1, recovered);
        assertEquals(ProjectSummaryStatus.FAILED, stored.getSummaryStatus());
        assertTrue(stored.getSummaryError().contains("平台重启"), stored.getSummaryError());
    }

    private ProjectSummaryGenerationService service(ProjectSummaryGenerator generator) {
        return new ProjectSummaryGenerationService(projects, generator, Runnable::run);
    }

    private static final class InMemoryProjects implements ProjectRepository {

        private final Map<Long, Project> rows = new LinkedHashMap<>();
        private long sequence;

        Project create(String code, String path) {
            Project project = Project.register(code, "项目 " + code, path,
                    "Java 17 / Maven", "mvn test", DecisionLevel.L1);
            save(project);
            return project;
        }

        @Override
        public boolean existsByCode(String code) {
            return rows.values().stream().anyMatch(project -> project.getCode().equals(code));
        }

        @Override
        public void save(Project project) {
            project.assignIdentity(ProjectId.of(++sequence));
            rows.put(project.getId().value(), project);
        }

        @Override
        public Optional<Project> findById(ProjectId id) {
            return Optional.ofNullable(rows.get(id.value()));
        }

        @Override
        public List<Project> findBySummaryStatus(ProjectSummaryStatus status) {
            return rows.values().stream()
                    .filter(project -> project.getSummaryStatus() == status)
                    .toList();
        }

        @Override
        public void update(Project project) {
            rows.put(project.getId().value(), project);
        }
    }
}
