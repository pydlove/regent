package com.pydlove.regent.project.application.query;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectVersionSource;
import com.pydlove.regent.project.domain.port.ProjectVersionReader;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.Objects;

/** 按项目配置解析本次可用的发布版本。 */
public class ProjectVersionService {

    private final ProjectProfileRepository projectProfileRepository;
    private final ProjectVersionReader projectVersionReader;

    public ProjectVersionService(ProjectProfileRepository projectProfileRepository,
                                 ProjectVersionReader projectVersionReader) {
        this.projectProfileRepository = Objects.requireNonNull(
                projectProfileRepository, "项目画像仓储");
        this.projectVersionReader = Objects.requireNonNull(projectVersionReader, "项目版本读取端口");
    }

    public ProjectVersionView resolve(ProjectId projectId) {
        return resolve(projectId, null);
    }

    public ProjectVersionView resolve(ProjectId projectId, UserId ownerId) {
        ProjectProfile project = (ownerId == null
                ? projectProfileRepository.findById(projectId)
                : projectProfileRepository.findById(projectId, ownerId))
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + projectId.value()));
        if (project.path() == null) {
            return new ProjectVersionView(null, "NONE", false, "项目尚未绑定本机代码目录");
        }
        if (project.versionSource() == ProjectVersionSource.MANUAL) {
            return project.fallbackVersion() == null
                    ? new ProjectVersionView(null, "NONE", false, "项目尚未设置固定版本")
                    : new ProjectVersionView(
                            project.fallbackVersion(), "MANUAL", true, "使用项目设置的固定版本");
        }

        String tag = projectVersionReader.currentTag(project.path()).orElse(null);
        if (tag != null && !tag.isBlank()) {
            return new ProjectVersionView(tag, "GIT_TAG", true, "使用当前提交的 Git Tag");
        }
        if (project.fallbackVersion() != null) {
            return new ProjectVersionView(
                    project.fallbackVersion(), "FALLBACK", true, "当前提交没有 Git Tag，使用备用版本");
        }
        return new ProjectVersionView(
                null, "NONE", false, "当前提交没有 Git Tag，且项目未设置备用版本");
    }
}
