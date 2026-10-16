package com.pydlove.regent.project.application.query;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.port.ProjectGitBranchReader;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 项目 Git 分支查询测试。
 *
 * <p>它守住“先按项目 ID 找到工作区、再读分支”这一步，避免接口层自己拼路径或绕过项目存在性校验。
 */
class ProjectGitBranchQueryServiceTest {

    @Test
    @DisplayName("按项目画像中的工作区路径读取当前分支")
    void readsBranchFromProjectWorkspace() {
        ProjectId projectId = ProjectId.of(7L);
        ProjectProfile profile = new ProjectProfile(
                projectId, "regent", "Regent", "/workspace/regent", DecisionLevel.L1, true);
        RecordingReader reader = new RecordingReader(Optional.of("codex/task-60"));
        ProjectGitBranchQueryService service = new ProjectGitBranchQueryService(
                new Profiles(profile), reader);

        assertEquals(Optional.of("codex/task-60"), service.currentBranch(projectId));
        assertEquals("/workspace/regent", reader.requestedPath);
    }

    @Test
    @DisplayName("项目没有 Git 管理时返回空")
    void returnsEmptyWhenWorkspaceHasNoGit() {
        ProjectId projectId = ProjectId.of(8L);
        ProjectProfile profile = new ProjectProfile(
                projectId, "plain", "普通目录", "/workspace/plain", DecisionLevel.L1, true);
        ProjectGitBranchQueryService service = new ProjectGitBranchQueryService(
                new Profiles(profile), new RecordingReader(Optional.empty()));

        assertEquals(Optional.empty(), service.currentBranch(projectId));
    }

    @Test
    @DisplayName("项目不存在时明确报错，不偷偷返回无分支")
    void rejectsUnknownProject() {
        ProjectGitBranchQueryService service = new ProjectGitBranchQueryService(
                new Profiles(null), new RecordingReader(Optional.empty()));

        assertThrows(EntityNotFoundException.class,
                () -> service.currentBranch(ProjectId.of(99L)));
    }

    private record Profiles(ProjectProfile profile) implements ProjectProfileRepository {

        @Override
        public Optional<ProjectProfile> findById(ProjectId id) {
            return Optional.ofNullable(profile).filter(it -> it.id().equals(id));
        }

        @Override
        public Optional<ProjectProfile> findByCode(String code) {
            return Optional.ofNullable(profile).filter(it -> it.code().equals(code));
        }

        @Override
        public List<ProjectProfile> findAll() {
            return profile == null ? List.of() : List.of(profile);
        }
    }

    private static final class RecordingReader implements ProjectGitBranchReader {

        private final Optional<String> branch;
        private String requestedPath;

        private RecordingReader(Optional<String> branch) {
            this.branch = branch;
        }

        @Override
        public Optional<String> currentBranch(String projectPath) {
            requestedPath = projectPath;
            return branch;
        }
    }
}
