package com.pydlove.regent.project.application.command;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.CodeEditorDescriptor;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.port.CodeEditorGateway;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.List;

/** 项目代码编辑器检测与打开用例。 */
public class CodeEditorService {

    private final ProjectProfileRepository projectProfileRepository;
    private final CodeEditorGateway codeEditorGateway;

    public CodeEditorService(ProjectProfileRepository projectProfileRepository,
                             CodeEditorGateway codeEditorGateway) {
        this.projectProfileRepository = projectProfileRepository;
        this.codeEditorGateway = codeEditorGateway;
    }

    public List<CodeEditorDescriptor> available(ProjectId projectId, UserId ownerId) {
        requireProject(projectId, ownerId);
        return codeEditorGateway.availableEditors();
    }

    public void open(OpenProjectCodeCommand command) {
        ProjectProfile project = requireProject(command.projectId(), command.ownerId());
        if (project.path() == null) {
            throw new DomainException("项目尚未绑定本机代码目录，暂时不能打开代码");
        }
        String relativePath = normalizeRelative(command.relativePath());
        Integer line = command.line();
        if (line != null && line < 1) {
            throw new DomainException("代码行号必须从 1 开始");
        }
        if (relativePath == null && line != null) {
            throw new DomainException("没有指定文件时不能携带代码行号");
        }
        codeEditorGateway.open(project.path(), relativePath, line, command.editorId());
    }

    private ProjectProfile requireProject(ProjectId projectId, UserId ownerId) {
        return projectProfileRepository.findById(projectId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "项目不存在：" + projectId.value()));
    }

    private static String normalizeRelative(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String path = value.replace('\\', '/').strip();
        if (path.startsWith("/") || path.contains("../") || path.equals("..")) {
            throw new DomainException("代码文件必须位于项目内：" + value);
        }
        return path;
    }
}
