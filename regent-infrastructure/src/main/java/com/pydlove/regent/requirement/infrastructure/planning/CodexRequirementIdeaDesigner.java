package com.pydlove.regent.requirement.infrastructure.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.execution.domain.model.AgentAccessMode;
import com.pydlove.regent.execution.domain.model.ExecRequest;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.port.AgentExecutor;
import com.pydlove.regent.requirement.domain.model.RequirementIdeaDraft;
import com.pydlove.regent.requirement.domain.model.RequirementIdeaRequest;
import com.pydlove.regent.requirement.domain.port.RequirementIdeaDesigner;
import com.pydlove.regent.shared.domain.DomainException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 用 Codex 把产品想法整理为结构化需求草稿。 */
public class CodexRequirementIdeaDesigner implements RequirementIdeaDesigner {

    private final AgentExecutor agentExecutor;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public CodexRequirementIdeaDesigner(AgentExecutor agentExecutor,
                                        ObjectMapper objectMapper,
                                        Duration timeout) {
        this.agentExecutor = agentExecutor;
        this.objectMapper = objectMapper;
        this.timeout = timeout;
    }

    @Override
    public List<RequirementIdeaDraft> design(RequirementIdeaRequest request) {
        Path temporaryWorkspace = null;
        ExecResult result;
        try {
            String workspace = request.projectPath();
            if (workspace == null || workspace.isBlank()) {
                // 需求拆分只依赖想法和项目资料；没有代码目录时给 Codex 一个空只读工作区，
                // 避免把“分析代码”的前置条件错误地传导到产品需求生成。
                temporaryWorkspace = Files.createTempDirectory("regent-requirement-idea-");
                workspace = temporaryWorkspace.toString();
            }
            result = agentExecutor.execute(new ExecRequest(
                    request.projectId(),
                    workspace,
                    prompt(request),
                    null,
                    timeout,
                    List.of(),
                    Map.of(),
                    null,
                    AgentAccessMode.READ_ONLY), (type, payload) -> {
            });
        } catch (IOException exception) {
            throw new DomainException("AI 需求生成工作区创建失败：" + exception.getMessage());
        } finally {
            deleteQuietly(temporaryWorkspace);
        }
        if (result.status() != RunStatus.SUCCESS || !result.schemaValid()) {
            throw new DomainException("AI 生成需求未完成（" + result.status() + "）："
                    + textOrUnknown(result.summary()));
        }
        if (!result.changedFiles().isEmpty()) {
            throw new DomainException("AI 生成需求时修改了项目文件，结果不可信");
        }
        return parse(result.summary());
    }

    List<RequirementIdeaDraft> parse(String summary) {
        if (summary == null || summary.isBlank()) {
            throw new DomainException("Codex 未返回需求草稿");
        }
        String json = summary.trim();
        if (json.startsWith("```")) {
            int firstLine = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                json = json.substring(firstLine + 1, lastFence).trim();
            }
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode requirements = root.path("requirements");
            if (!requirements.isArray() || requirements.isEmpty()) {
                throw new DomainException("AI 没有返回可入库的需求");
            }
            if (requirements.size() > 12) {
                throw new DomainException("一次最多生成 12 条需求");
            }
            List<RequirementIdeaDraft> drafts = new ArrayList<>();
            for (JsonNode item : requirements) {
                String title = text(item, "title");
                String rawText = text(item, "rawText");
                if (title.isBlank() || rawText.isBlank()) {
                    throw new DomainException("AI 生成的需求缺少标题或描述");
                }
                drafts.add(new RequirementIdeaDraft(
                        title,
                        rawText,
                        text(item, "acceptanceText"),
                        strings(item.path("questions")),
                        strings(item.path("assumptions"))));
            }
            return List.copyOf(drafts);
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DomainException("Codex 返回的需求草稿不是合法 JSON", exception);
        }
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    values.add(item.asText().trim());
                }
            }
        }
        return values;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private static String prompt(RequirementIdeaRequest request) {
        return """
                你正在作为 Regent 的产品经理，把一段产品想法拆解成需求池中的多个需求草稿。

                项目：%s（%s）
                项目目录：%s
                项目资料：
                %s

                原始想法：
                %s

                请严格输出 JSON，不要 Markdown，不要解释：
                {
                  "requirements": [
                    {
                      "title": "不超过 60 字的需求标题",
                      "rawText": "结构化的需求描述，包含背景、问题、目标和范围",
                      "acceptanceText": "可验证的验收标准，每行一条",
                      "questions": ["仍需产品确认的问题，最多 5 个"],
                      "assumptions": ["当前采用但尚待确认的假设"]
                    }
                  ]
                }

                规则：
                1. 不得修改任何文件，只做只读分析。
                2. 不编造业务事实；不确定的内容放进 questions 或 assumptions。
                3. 需求描述必须让研发能理解业务价值和交付边界。
                4. 所有正文使用简体中文。
                5. 优先拆成 2-8 个可以独立交付、独立验收、独立评审的需求；不要按技术任务拆分。
                6. 如果想法确实不可拆分，可以只返回 1 条，但不要为了避免拆分成倍复制同一需求。
                """.formatted(
                request.projectName(),
                request.projectCode(),
                request.projectPath() == null || request.projectPath().isBlank()
                        ? "未绑定；本次只基于项目名称、项目资料和产品想法拆分，不分析代码"
                        : request.projectPath(),
                textOrNone(request.projectSummary()),
                request.idea());
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时只读工作区清理失败不影响已经得到的模型结果。
                }
            });
        } catch (IOException ignored) {
            // 同上：异常留给下一次系统临时目录清理。
        }
    }

    private static String textOrNone(String value) {
        return value == null || value.isBlank() ? "未提供" : value.trim();
    }

    private static String textOrUnknown(String value) {
        return value == null || value.isBlank() ? "未返回原因" : value.trim();
    }
}
