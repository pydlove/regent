package com.pydlove.regent.project.application.command;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectSummaryStatus;
import com.pydlove.regent.project.domain.repository.ProjectRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注册用例测试。
 *
 * <p>它守的是「一次误注册的成本」：标识撞车和路径写错都必须在这一刻失败 ——
 * 前者会让两个项目共用一条执行历史，后者要等到第一次派发才炸，那时报错躺在执行记录里。
 */
class ProjectRegistrationServiceTest {

    /** 只记账的写侧替身：注册用例的断言都在「保存了什么 / 有没有保存」上。 */
    private static final class Registry implements ProjectRepository {

        private final List<Project> saved = new ArrayList<>();

        @Override
        public boolean existsByCode(String code) {
            return saved.stream().anyMatch(project -> project.getCode().equals(code));
        }

        @Override
        public void save(Project project) {
            project.assignIdentity(ProjectId.of(saved.size() + 1L));
            saved.add(project);
        }

        @Override
        public Optional<Project> findById(ProjectId id) {
            return saved.stream().filter(project -> project.getId().equals(id)).findFirst();
        }

        @Override
        public List<Project> findBySummaryStatus(ProjectSummaryStatus status) {
            return saved.stream().filter(project -> project.getSummaryStatus() == status).toList();
        }

        @Override
        public void update(Project project) {
            // 替身保存的是同一个对象，状态已就地更新。
        }

        @Override
        public void updateDetails(Project project) {
            // 注册用例不会调用编辑写侧。
        }
    }

    private final Registry projects = new Registry();
    private final ProjectRegistrationService service = new ProjectRegistrationService(projects);

    private static RegisterProjectCommand command(String code, String path) {
        return new RegisterProjectCommand(code, "审计平台", path, null, null, DecisionLevel.L1);
    }

    @Test
    @DisplayName("目录存在就注册成功，并把回填的 ID 带回聚合")
    void registersExistingDirectory(@TempDir Path workspace) {
        Project project = service.register(command("app_audit", workspace.toString()));

        assertEquals(ProjectId.of(1L), project.getId());
        assertEquals(1, projects.saved.size());
        assertEquals(workspace.toString(), projects.saved.get(0).getPath());
    }

    @Test
    @DisplayName("注册时把项目归到当前账号名下")
    void recordsOwner(@TempDir Path workspace) {
        UserId ownerId = UserId.of(42L);

        Project project = service.register(command("app_audit", workspace.toString()), ownerId);

        assertEquals(ownerId, project.getOwnerId());
        assertEquals(ownerId, projects.saved.get(0).getOwnerId());
    }

    @Test
    @DisplayName("标识已被占用时拒绝，且不再落库")
    void rejectsDuplicatedCode(@TempDir Path workspace) {
        service.register(command("app_audit", workspace.toString()));

        assertThrows(DomainException.class,
                () -> service.register(command("app_audit", workspace.toString())));
        assertEquals(1, projects.saved.size(), "被拒绝的注册不该留下第二行");
    }

    @Test
    @DisplayName("路径不存在时拒绝 —— 写错一个字符不该等到派发才发现")
    void rejectsMissingDirectory() {
        assertThrows(DomainException.class,
                () -> service.register(command("app_audit", "/tmp/regent-not-exists-9f3a")));
        assertTrue(projects.saved.isEmpty());
    }

    @Test
    @DisplayName("路径指向文件而不是目录时同样拒绝：codex 需要的是工作区")
    void rejectsRegularFile(@TempDir Path workspace) throws Exception {
        Path file = Files.writeString(workspace.resolve("pom.xml"), "<project/>");

        assertThrows(DomainException.class, () -> service.register(command("app_audit", file.toString())));
        assertTrue(projects.saved.isEmpty());
    }

    @Test
    @DisplayName("注册保存成功后只调度一次项目资料生成")
    void schedulesSummaryGenerationAfterSave(@TempDir Path workspace) {
        List<ProjectId> scheduled = new ArrayList<>();
        TransactionRunner direct = new TransactionRunner() {
            @Override
            public <T> T inTransaction(Supplier<T> action) {
                return action.get();
            }
        };
        ProjectRegistrationService orchestrating = new ProjectRegistrationService(
                projects, scheduled::add, direct);

        Project project = orchestrating.register(command("app_audit", workspace.toString()));

        assertEquals(List.of(project.getId()), scheduled);
    }

    @Test
    @DisplayName("资料生成在事务提交后才启动，回滚不会误触发 Codex")
    void schedulesSummaryOnlyAfterCommit(@TempDir Path workspace) {
        List<ProjectId> scheduled = new ArrayList<>();
        List<Runnable> commits = new ArrayList<>();
        TransactionRunner deferred = new TransactionRunner() {
            @Override
            public <T> T inTransaction(Supplier<T> action) {
                return action.get();
            }

            @Override
            public void afterCompletion(Runnable onCommit, Runnable onRollback) {
                commits.add(onCommit);
            }
        };
        ProjectRegistrationService orchestrating = new ProjectRegistrationService(
                projects, scheduled::add, deferred);

        Project project = orchestrating.register(command("app_audit", workspace.toString()));

        assertTrue(scheduled.isEmpty(), "事务完成前不能启动后台 Codex");
        commits.forEach(Runnable::run);
        assertEquals(List.of(project.getId()), scheduled);
    }
}
