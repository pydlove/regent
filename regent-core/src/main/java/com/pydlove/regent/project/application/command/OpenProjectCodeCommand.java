package com.pydlove.regent.project.application.command;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.CodeEditorId;
import com.pydlove.regent.project.domain.model.ProjectId;

/** 使用本机已安装编辑器打开项目或指定文件。 */
public record OpenProjectCodeCommand(
        ProjectId projectId,
        UserId ownerId,
        CodeEditorId editorId,
        String relativePath,
        Integer line) {
}
