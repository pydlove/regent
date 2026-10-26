package com.pydlove.regent.boot.config;

import com.pydlove.regent.project.application.command.CodeEditorService;
import com.pydlove.regent.project.domain.port.CodeEditorGateway;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.project.infrastructure.editor.LocalCodeEditorGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 本机代码编辑器集成装配。 */
@Configuration
public class CodeEditorConfig {

    @Bean
    public CodeEditorGateway codeEditorGateway() {
        return new LocalCodeEditorGateway();
    }

    @Bean
    public CodeEditorService codeEditorService(
            ProjectProfileRepository projectProfileRepository,
            CodeEditorGateway codeEditorGateway) {
        return new CodeEditorService(projectProfileRepository, codeEditorGateway);
    }
}
