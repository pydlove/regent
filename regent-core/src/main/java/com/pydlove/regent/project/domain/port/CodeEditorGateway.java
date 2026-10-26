package com.pydlove.regent.project.domain.port;

import com.pydlove.regent.project.domain.model.CodeEditorDescriptor;
import com.pydlove.regent.project.domain.model.CodeEditorId;

import java.util.List;

/** 本机代码编辑器检测与打开端口。 */
public interface CodeEditorGateway {

    List<CodeEditorDescriptor> availableEditors();

    void open(String projectPath,
              String relativePath,
              Integer line,
              CodeEditorId editorId);
}
