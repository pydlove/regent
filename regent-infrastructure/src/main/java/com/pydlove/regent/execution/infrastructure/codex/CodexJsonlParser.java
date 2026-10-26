package com.pydlove.regent.execution.infrastructure.codex;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.shared.kernel.TokenUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code codex exec --json} 事件流的解析 —— 防腐层里最薄也最关键的一层。
 *
 * <p>它只做一件事：把一行文本变成平台认得的事件，外加从里面挑出续跑要用的 {@code thread_id}
 * 与 token 用量。真正的结构（哪种事件里有什么）在这里一次性解释完，出了这个包，
 * 平台的其他部分就不该再出现 {@code thread_id} 这种字段名的字面量。
 *
 * <h2>为什么非 JSON 行不算错误</h2>
 * 子进程的 stdout 里混进构建工具的告警、进度条、甚至半行日志是常态。把这些当解析失败处理，
 * 会导致一次本来成功的执行因为「输出里多了一行 warning」而判为失败 —— 那是把工具链的噪音
 * 当成业务事实。所以解析不了就降级成 {@code raw} 事件原样存档，只有末条消息读不懂才判失败。
 */
public final class CodexJsonlParser {

    /** 非 JSON 行、或没有 {@code type} 字段的 JSON 行，都归到这个类型，保证原文不丢。 */
    static final String RAW_EVENT = "raw";

    private static final String FIELD_TYPE = "type";
    private static final String FIELD_THREAD_ID = "thread_id";
    private static final String FIELD_USAGE = "usage";
    private static final String FIELD_MESSAGE = "message";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_INPUT_TOKENS = "input_tokens";
    private static final String FIELD_CACHED_INPUT_TOKENS = "cached_input_tokens";
    private static final String FIELD_OUTPUT_TOKENS = "output_tokens";

    private final ObjectMapper mapper = new ObjectMapper();

    /** 解析一行 stdout。空行直接忽略。 */
    public Optional<ParsedEvent> parse(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        JsonNode node = readTree(line).orElse(null);
        if (node == null || !node.isObject()) {
            return Optional.of(new ParsedEvent(RAW_EVENT, null, null, escaped(line), null));
        }
        String type = node.path(FIELD_TYPE).asText(RAW_EVENT);
        String threadId = node.path(FIELD_THREAD_ID).asText(null);
        TokenUsage usage = readUsage(node);
        return Optional.of(new ParsedEvent(type, threadId, usage, line, diagnostic(type, node)));
    }

    /**
     * 解析 {@code -o} 落盘的末条消息。
     *
     * <p>读不懂返回 empty，由调用方按「出参不合法」处理：这里不抛异常，是因为「读不懂」
     * 本身就是一个需要被记录下来的结论，而不是让整条链路崩掉的错误。
     */
    public Optional<CodexReport> parseReport(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        JsonNode node = readTree(raw).orElse(null);
        if (node == null || !node.isObject()) {
            return Optional.empty();
        }
        JsonNode decision = node.path("decision");
        JsonNode branch = node.path("branch");
        boolean hasDecision = decision.isObject();
        boolean hasBranch = branch.isObject();
        return Optional.of(new CodexReport(
                text(node, "status"),
                text(node, "summary"),
                stringList(node.path("changedFiles")),
                hasDecision ? text(decision, "level") : null,
                hasDecision ? text(decision, "question") : null,
                hasDecision ? stringList(decision.path("options")) : List.of(),
                hasDecision ? text(decision, "recommendation") : null,
                hasDecision ? decision.path("reversible").asBoolean(false) : false,
                hasDecision ? text(decision, "blastRadius") : null,
                bugs(node.path("bugs")),
                hasBranch ? branch.path("requested").asBoolean(false) : false,
                hasBranch ? text(branch, "name") : null,
                hasBranch ? text(branch, "reason") : null));
    }

    private Optional<JsonNode> readTree(String raw) {
        try {
            return Optional.of(mapper.readTree(raw));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private TokenUsage readUsage(JsonNode node) {
        JsonNode usage = node.path(FIELD_USAGE);
        if (!usage.isObject()) {
            return null;
        }
        return TokenUsage.of(
                usage.path(FIELD_INPUT_TOKENS).asLong(0),
                usage.path(FIELD_CACHED_INPUT_TOKENS).asLong(0),
                usage.path(FIELD_OUTPUT_TOKENS).asLong(0));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    /**
     * Codex 会把模型服务的失败放在 {@code error}/{@code turn.failed} 事件里，而不是 stderr。
     * 优先取出内层可读原因，避免界面只看到包裹了一层的 JSON。
     */
    private String diagnostic(String type, JsonNode node) {
        String raw;
        if ("turn.failed".equals(type)) {
            raw = text(node.path(FIELD_ERROR), FIELD_MESSAGE);
        } else if ("error".equals(type)) {
            raw = text(node, FIELD_MESSAGE);
        } else {
            return null;
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        JsonNode nested = readTree(raw).orElse(null);
        if (nested != null && nested.isObject()) {
            String readable = text(nested.path(FIELD_ERROR), FIELD_MESSAGE);
            if (readable != null && !readable.isBlank()) {
                return readable;
            }
        }
        return raw.strip();
    }

    private static List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (!item.isNull()) {
                values.add(item.asText());
            }
        });
        return values;
    }

    private static List<CodexBug> bugs(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<CodexBug> values = new ArrayList<>();
        node.forEach(item -> {
            if (item.isObject()) {
                values.add(new CodexBug(
                        text(item, "title"),
                        text(item, "severity"),
                        text(item, "reproduction"),
                        text(item, "filePath"),
                        text(item, "detail")));
            }
        });
        return values;
    }

    /** 把一行非 JSON 文本包成合法的 JSON 字符串，好让它能原样存进 JSON 列。 */
    private String escaped(String line) {
        try {
            return mapper.writeValueAsString(line);
        } catch (JsonProcessingException impossible) {
            return null;
        }
    }

    /**
     * 一行事件。
     *
     * @param type      事件类型；解析不出来时是 {@link #RAW_EVENT}
     * @param threadId  会话 ID，只有 {@code thread.started} 那类事件才带
     * @param usage     token 用量，只有 {@code turn.completed} 带；其余为 null
     * @param payloadJson 存货原文，保证是合法 JSON
     * @param diagnostic 执行器返回的可读错误原因；非错误事件为 null
     */
    public record ParsedEvent(String type,
                              String threadId,
                              TokenUsage usage,
                              String payloadJson,
                              String diagnostic) {
    }

    /**
     * 末条消息的结构化形状。字段全是字符串或基本类型，没有领域枚举 ——
     * 把「DONE 对应 SUCCESS」这种翻译留在适配器里，解析器只负责「读出来」。
     */
    public record CodexReport(
            String status,
            String summary,
            List<String> changedFiles,
            String level,
            String question,
            List<String> options,
            String recommendation,
            Boolean reversible,
            String blastRadius,
            List<CodexBug> bugs,
            boolean branchRequested,
            String branchName,
            String branchReason) {

        public boolean hasDecision() {
            return level != null && !level.isBlank() && question != null && !question.isBlank();
        }
    }

    /** AI 发现的缺陷。缺项由领域层统一判为不合法，不在解析器里抛异常。 */
    public record CodexBug(
            String title,
            String severity,
            String reproduction,
            String filePath,
            String detail) {
    }
}
