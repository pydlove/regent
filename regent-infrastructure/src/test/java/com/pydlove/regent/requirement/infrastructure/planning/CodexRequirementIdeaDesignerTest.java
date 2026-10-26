package com.pydlove.regent.requirement.infrastructure.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.RequirementIdeaDraft;
import com.pydlove.regent.requirement.domain.model.RequirementIdeaRequest;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.TokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexRequirementIdeaDesignerTest {

    private final CodexRequirementIdeaDesigner designer =
            new CodexRequirementIdeaDesigner(null, new ObjectMapper(), Duration.ofSeconds(1));

    @Test
    @DisplayName("解析带 Markdown 代码块的 AI 需求草稿")
    void parsesFencedJsonDraft() {
        List<RequirementIdeaDraft> drafts = designer.parse("""
                ```json
                {
                  "requirements": [
                    {
                      "title": "组织数据隔离",
                      "rawText": "不同组织的数据必须完全隔离。",
                      "acceptanceText": "账号不能跨组织查询数据",
                      "questions": ["历史数据如何归属？"],
                      "assumptions": ["沿用现有组织模型"]
                    },
                    {
                      "title": "组织成员角色分配",
                      "rawText": "管理员可以给成员分配组织角色。",
                      "acceptanceText": "角色变更立即生效"
                    }
                  ]
                }
                ```
                """);

        assertEquals(2, drafts.size());
        assertEquals("组织数据隔离", drafts.get(0).title());
        assertEquals(1, drafts.get(0).questions().size());
        assertEquals("沿用现有组织模型", drafts.get(0).assumptions().get(0));
        assertEquals("组织成员角色分配", drafts.get(1).title());
    }

    @Test
    @DisplayName("AI 草稿缺少标题或描述时拒绝落库")
    void rejectsIncompleteDraft() {
        assertThrows(DomainException.class,
                () -> designer.parse("{\"requirements\":[{\"title\":\"只有标题\"}]}"));
        assertThrows(DomainException.class,
                () -> designer.parse("not-json"));
    }

    @Test
    @DisplayName("项目未绑定代码目录时使用临时只读工作区")
    void usesTemporaryWorkspaceWhenProjectPathIsMissing() {
        AtomicReference<Path> workspace = new AtomicReference<>();
        CodexRequirementIdeaDesigner pathlessDesigner = new CodexRequirementIdeaDesigner(
                (request, listener) -> {
                    Path path = Path.of(request.projectPath());
                    workspace.set(path);
                    assertTrue(Files.isDirectory(path));
                    return new ExecResult(
                            RunStatus.SUCCESS,
                            0,
                            "thread-idea",
                            TokenUsage.ZERO,
                            """
                            {"requirements":[{"title":"组织隔离","rawText":"组织之间数据隔离。",
                            "acceptanceText":"不能跨组织查询"}]}
                            """,
                            "ok",
                            true,
                            List.of(),
                            List.of(),
                            null);
                },
                new ObjectMapper(),
                Duration.ofSeconds(1));

        List<RequirementIdeaDraft> drafts = pathlessDesigner.design(
                new RequirementIdeaRequest(
                        ProjectId.of(9L),
                        "regent",
                        "Regent",
                        null,
                        null,
                        "希望组织数据隔离"));

        assertEquals(1, drafts.size());
        assertTrue(workspace.get().startsWith(Path.of(System.getProperty("java.io.tmpdir"))));
        assertFalse(Files.exists(workspace.get()));
    }
}
