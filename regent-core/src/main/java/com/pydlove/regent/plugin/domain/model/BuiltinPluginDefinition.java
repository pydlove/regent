package com.pydlove.regent.plugin.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.List;

/** 发行包中声明的内置插件定义。 */
public record BuiltinPluginDefinition(
        String key,
        String name,
        String description,
        List<String> tags,
        String filename,
        String content,
        boolean defaultEnabled) {

    public BuiltinPluginDefinition {
        key = requireKey(key);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new DomainException("内置插件标识不能为空");
        }
        String value = key.trim();
        if (value.length() > 64 || !value.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new DomainException("内置插件标识必须是 1-64 位小写字母、数字或连字符：" + value);
        }
        return value;
    }
}
