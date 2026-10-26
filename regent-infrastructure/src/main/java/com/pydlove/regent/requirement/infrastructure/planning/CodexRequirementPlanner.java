package com.pydlove.regent.requirement.infrastructure.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.execution.domain.model.AgentAccessMode;
import com.pydlove.regent.execution.domain.model.ExecRequest;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.port.AgentExecutor;
import com.pydlove.regent.requirement.domain.model.RequirementPlanningRequest;
import com.pydlove.regent.requirement.domain.model.RequirementPlanningResult;
import com.pydlove.regent.requirement.domain.model.RequirementTaskDraft;
import com.pydlove.regent.requirement.domain.port.RequirementPlanner;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 使用当前执行设备上的 Codex 做需求澄清与任务拆解。 */
public class CodexRequirementPlanner implements RequirementPlanner {

    private final AgentExecutor agentExecutor;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public CodexRequirementPlanner(AgentExecutor agentExecutor,
                                   ObjectMapper objectMapper,
                                   Duration timeout) {
        this.agentExecutor = agentExecutor;
        this.objectMapper = objectMapper;
        this.timeout = timeout;
    }

    @Override
    public RequirementPlanningResult analyze(RequirementPlanningRequest request) {
        ExecResult result = agentExecutor.execute(new ExecRequest(
                request.projectId(),
                request.projectPath(),
                prompt(request),
                null,
                timeout,
                List.of(),
                Map.of(),
                null,
                AgentAccessMode.READ_ONLY), (type, payload) -> {
        });
        if (result.status() != RunStatus.SUCCESS || !result.schemaValid()) {
            throw new DomainException("需求分析未完成（" + result.status() + "）："
                    + textOrUnknown(result.summary()));
        }
        if (!result.changedFiles().isEmpty()) {
            throw new DomainException("需求分析过程修改了项目文件，结果不可信：" + result.changedFiles());
        }
        return parse(result.summary());
    }

    RequirementPlanningResult parse(String summary) {
        if (summary == null || summary.isBlank()) {
            throw new DomainException("Codex 未返回需求分析结果");
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
            List<String> questions = strings(root.path("questions"));
            List<String> assumptions = strings(root.path("assumptions"));
            List<RequirementTaskDraft> drafts = new ArrayList<>();
            for (JsonNode node : root.path("tasks")) {
                drafts.add(new RequirementTaskDraft(
                        text(node, "key"),
                        text(node, "title"),
                        text(node, "objective"),
                        strings(node.path("scope")),
                        strings(node.path("outOfScope")),
                        strings(node.path("acceptanceCriteria")),
                        strings(node.path("dependencies")),
                        node.path("priority").asInt(0),
                        text(node, "risk"),
                        node.path("suggestedOrder").asInt(drafts.size() + 1)));
            }
            return new RequirementPlanningResult(questions, assumptions, drafts);
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException("Codex 返回的需求分析结果不是合法 JSON", e);
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
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String prompt(RequirementPlanningRequest request) {
        return """
                你正在作为 Regent 的需求分析师，只读分析需求并生成可执行任务草稿。

                项目：%s（%s）
                项目目录：%s
                需求标题：%s
                需求描述：
                %s

                用户填写的验收标准：
                %s

                已有澄清补充：
                %s

                项目资料：
                %s

                请严格输出 JSON，不要 Markdown，不要解释：
                {
                  "questions": ["最多 3 个必须澄清的问题；信息足够时为空数组"],
                  "assumptions": ["用户未回答但系统采用的假设"],
                  "tasks": [
                    {
                      "key": "T1",
                      "title": "短标题",
                      "objective": "可执行目标",
                      "scope": ["工作范围"],
                      "outOfScope": ["非目标"],
                      "acceptanceCriteria": ["可验证验收标准"],
                      "dependencies": ["T0"],
                      "priority": 0,
                      "risk": "L0-L3",
                      "suggestedOrder": 1
                    }
                  ]
                }

                规则：
                1. 先判断信息是否足够。关键歧义未解决时，questions 给 1-3 个问题，tasks 必须为空。
                2. 信息足够时 questions 为空，任务数量 1-12 个。
                3. 每个任务必须有目标、范围和至少一个可验证验收标准。
                4. 不要拆出“分析需求”“完成开发”这类空任务。
                5. 依赖只能引用本次任务 key，不能成环。
                6. 一个需求只属于一个项目，不要生成跨项目任务。
                7. 不得修改任何文件，不得请求分支或人工决策。
                8. 所有正文使用简体中文。
                """.formatted(
                request.projectName(),
                request.projectCode(),
                request.projectPath(),
                request.title(),
                request.rawText(),
                textOrNone(request.acceptanceText()),
                textOrNone(request.clarificationText()),
                textOrNone(request.projectSummary()));
    }

    private static String textOrNone(String value) {
        return value == null || value.isBlank() ? "未填写" : value.trim();
    }

    private static String textOrUnknown(String value) {
        return value == null || value.isBlank() ? "未返回原因" : value.trim();
    }
}
