package com.pydlove.regent.project.application.query;

import com.pydlove.regent.project.domain.model.EnvironmentEntry;
import com.pydlove.regent.project.domain.model.EnvironmentScope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 某个项目实际生效的环境字段。
 *
 * <p>输入按仓储约定先全局、后项目。这里显式按范围排序，确保同名键始终由项目级覆盖，
 * 不把覆盖语义偷偷依赖在 SQL 排序上。
 */
public record EnvironmentContext(List<Variable> variables) {

    public EnvironmentContext {
        variables = variables == null ? List.of() : List.copyOf(variables);
    }

    public static EnvironmentContext resolve(List<EnvironmentEntry> entries) {
        Map<String, EnvironmentEntry> effective = new LinkedHashMap<>();
        entries.stream()
                .sorted(Comparator.comparing(entry -> entry.getScope() == EnvironmentScope.GLOBAL ? 0 : 1))
                .forEach(entry -> effective.put(entry.getKey(), entry));

        List<Variable> variables = effective.values().stream()
                .sorted(Comparator.comparing(EnvironmentEntry::getKey))
                .map(entry -> new Variable(
                        entry.getKey(),
                        entry.getValue(),
                        entry.getScope().name(),
                        entry.getTags(),
                        entry.getDescription()))
                .toList();
        return new EnvironmentContext(variables);
    }

    public Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        for (Variable variable : variables) {
            values.put(variable.key(), variable.value());
        }
        return Map.copyOf(values);
    }

    public boolean isEmpty() {
        return variables.isEmpty();
    }

    /** 给模型看的键清单。值只进入进程环境或工作包，不写进可持久化的任务指令。 */
    public String promptSection() {
        if (variables.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder()
                .append("\n\n## 可用环境信息\n")
                .append("以下字段已随本次执行提供，值不会写进指令：\n");
        for (Variable variable : variables) {
            text.append("- `").append(variable.key()).append('`');
            List<String> annotations = new ArrayList<>();
            annotations.add(variable.scope().equals(EnvironmentScope.GLOBAL.name()) ? "全局" : "项目");
            if (!variable.tags().isEmpty()) {
                annotations.add("标签：" + String.join("、", variable.tags()));
            }
            text.append("（").append(String.join("；", annotations)).append("）");
            if (variable.description() != null) {
                text.append("：").append(variable.description());
            }
            text.append('\n');
        }
        return text.toString();
    }

    public record Variable(
            String key,
            String value,
            String scope,
            List<String> tags,
            String description) {

        public Variable {
            Objects.requireNonNull(key, "环境键不能为空");
            value = value == null ? "" : value;
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }
}
