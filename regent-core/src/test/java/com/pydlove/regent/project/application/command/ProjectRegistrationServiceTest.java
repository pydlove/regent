package com.pydlove.regent.project.application.command;

import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
}
