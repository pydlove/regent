package com.pydlove.regent.project.application.command;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.CodeEditorDescriptor;
import com.pydlove.regent.project.domain.model.CodeEditorId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.port.CodeEditorGateway;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CodeEditorServiceTest {

    private final ExecutionFakes.Projects projects = new ExecutionFakes.Projects()
            .put(ExecutionFakes.Projects.profile(1, DecisionLevel.L1, true));
    private final Gateway gateway = new Gateway();
    private final CodeEditorService service = new CodeEditorService(projects, gateway);

    @Test
    void 只允许打开当前账号项目内的代码文件() {
        service.open(new OpenProjectCodeCommand(
                ProjectId.of(1), UserId.of(7), CodeEditorId.VS_CODE,
                "src/main/App.java", 12));

        assertEquals("src/main/App.java", gateway.relativePath);
        assertEquals(12, gateway.line);
        assertEquals(CodeEditorId.VS_CODE, gateway.editorId);
    }

    @Test
    void 路径穿越和非法行号失败关闭() {
        assertThrows(DomainException.class, () -> service.open(new OpenProjectCodeCommand(
                ProjectId.of(1), UserId.of(7), CodeEditorId.CURSOR,
                "../secret.txt", null)));
        assertThrows(DomainException.class, () -> service.open(new OpenProjectCodeCommand(
                ProjectId.of(1), UserId.of(7), CodeEditorId.CURSOR,
                "src/main/App.java", 0)));
    }

    @Test
    void 看不到不属于当前账号的项目() {
        assertThrows(RuntimeException.class,
                () -> service.available(ProjectId.of(99), UserId.of(7)));
    }

    private static final class Gateway implements CodeEditorGateway {
        private String relativePath;
        private Integer line;
        private CodeEditorId editorId;

        @Override
        public List<CodeEditorDescriptor> availableEditors() {
            return List.of(new CodeEditorDescriptor(CodeEditorId.VS_CODE,
                    "Visual Studio Code", true));
        }

        @Override
        public void open(String projectPath, String relativePath, Integer line, CodeEditorId editorId) {
            this.relativePath = relativePath;
            this.line = line;
            this.editorId = editorId;
        }
    }
}
