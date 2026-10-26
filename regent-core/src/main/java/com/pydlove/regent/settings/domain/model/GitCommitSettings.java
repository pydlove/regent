package com.pydlove.regent.settings.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 企业级 Git 提交信息规范。
 *
 * <p>模板只负责生成建议，保存时仍按正则校验最终文本。企业可以保留 Conventional Commits，
 * 也可以改用带需求编号、部门前缀等自有格式。
 */
public record GitCommitSettings(
        boolean enabled,
        String defaultType,
        String defaultScope,
        String subjectTemplate,
        String bodyTemplate,
        String validationPattern,
        String validationHint) {

    public static final int MAX_TYPE_LENGTH = 32;
    public static final int MAX_SCOPE_LENGTH = 64;
    public static final int MAX_SUBJECT_TEMPLATE_LENGTH = 512;
    public static final int MAX_BODY_TEMPLATE_LENGTH = 4000;
    public static final int MAX_PATTERN_LENGTH = 1024;
    public static final int MAX_HINT_LENGTH = 512;

    public GitCommitSettings {
        defaultType = normalize(defaultType);
        defaultScope = normalize(defaultScope);
        subjectTemplate = normalize(subjectTemplate);
        bodyTemplate = bodyTemplate == null ? "" : bodyTemplate.strip();
        validationPattern = normalize(validationPattern);
        validationHint = normalize(validationHint);

        if (defaultType.length() > MAX_TYPE_LENGTH
                || (!defaultType.isEmpty() && !defaultType.matches("[A-Za-z0-9_-]+"))) {
            throw new DomainException("默认提交类型只能包含字母、数字、下划线和连字符");
        }
        if (defaultScope.length() > MAX_SCOPE_LENGTH
                || (!defaultScope.isEmpty() && !defaultScope.matches("[A-Za-z0-9._-]+"))) {
            throw new DomainException("默认提交范围只能包含字母、数字、点、下划线和连字符");
        }
        if (subjectTemplate.isEmpty() || subjectTemplate.length() > MAX_SUBJECT_TEMPLATE_LENGTH) {
            throw new DomainException("提交标题模板不能为空，且不能超过 "
                    + MAX_SUBJECT_TEMPLATE_LENGTH + " 个字符");
        }
        if (!subjectTemplate.contains("{title}") && !subjectTemplate.contains("{taskId}")) {
            throw new DomainException("提交标题模板必须包含 {title} 或 {taskId}");
        }
        if (bodyTemplate.length() > MAX_BODY_TEMPLATE_LENGTH) {
            throw new DomainException("提交正文模板不能超过 " + MAX_BODY_TEMPLATE_LENGTH + " 个字符");
        }
        if (validationPattern.length() > MAX_PATTERN_LENGTH) {
            throw new DomainException("提交校验表达式不能超过 " + MAX_PATTERN_LENGTH + " 个字符");
        }
        if (validationHint.length() > MAX_HINT_LENGTH) {
            throw new DomainException("校验提示不能超过 " + MAX_HINT_LENGTH + " 个字符");
        }
        compile(validationPattern);
    }

    public static GitCommitSettings defaults() {
        return new GitCommitSettings(
                true,
                "feat",
                "",
                "{type}({ticket}): {title}",
                "",
                "(?s)^(feat|fix|refactor|docs|test|build|chore|perf|ci|style)"
                        + "(\\(\\d{6}\\))?: [^\\r\\n]+(?:\\R\\R[\\s\\S]+)?$",
                "格式：type(6位Bug编号): 中文说明；未关联 Bug 时生成 type: 中文说明。"
                        + "例如 feat(102008): 优化告警记录处理逻辑，或 feat: 完善变更审阅");
    }

    public String render(String taskId,
                         String ticket,
                         String title,
                         String projectCode,
                         String projectName,
                         List<String> filePaths) {
        List<String> files = filePaths == null ? List.of() : filePaths;
        String scope = defaultScope.isEmpty()
                ? normalize(projectCode).toLowerCase()
                : defaultScope;
        String subject = expand(subjectTemplate, taskId, ticket, title, projectCode, projectName,
                scope, files).replaceAll("\\s+", " ").strip();
        String body = bodyTemplate.isEmpty()
                ? ""
                : expand(bodyTemplate, taskId, ticket, title, projectCode, projectName,
                        scope, files).strip();
        return body.isEmpty() ? subject : subject + "\n\n" + body;
    }

    public boolean requiresTicket() {
        return subjectTemplate.contains("{ticket}") || bodyTemplate.contains("{ticket}");
    }

    public void validateMessage(String message) {
        String normalized = normalize(message);
        if (normalized.isEmpty()) {
            throw new DomainException("Git 提交信息不能为空");
        }
        if (normalized.length() > 4000) {
            throw new DomainException("Git 提交信息不能超过 4000 个字符");
        }
        if (!validationPattern.isEmpty()
                && !compile(validationPattern).matcher(normalized).matches()) {
            throw new DomainException(validationHint.isEmpty()
                    ? "Git 提交信息不符合当前企业规范"
                    : validationHint);
        }
    }

    private String expand(String template,
                          String taskId,
                          String ticket,
                          String title,
                          String projectCode,
                          String projectName,
                          String scope,
                          List<String> files) {
        String normalizedTicket = normalize(ticket);
        String expanded = normalizedTicket.isEmpty()
                ? template.replace("({ticket})", "").replace("[{ticket}]", "")
                : template;
        return expanded
                .replace("{type}", defaultTypeOrDefault())
                .replace("{scope}", scope)
                .replace("{taskId}", normalize(taskId))
                .replace("{ticket}", normalizedTicket)
                .replace("{title}", normalize(title))
                .replace("{projectCode}", normalize(projectCode))
                .replace("{projectName}", normalize(projectName))
                .replace("{fileCount}", Integer.toString(files.size()))
                .replace("{files}", String.join("\n", files));
    }

    private String defaultTypeOrDefault() {
        return defaultType.isEmpty() ? "feat" : defaultType;
    }

    private static Pattern compile(String expression) {
        if (expression == null || expression.isEmpty()) {
            return Pattern.compile(".*");
        }
        try {
            return Pattern.compile(expression, Pattern.DOTALL);
        } catch (PatternSyntaxException exception) {
            throw new DomainException("Git 提交校验表达式不是合法正则", exception);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
