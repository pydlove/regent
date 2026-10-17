package com.pydlove.regent.plugin.infrastructure.builtin;

import com.pydlove.regent.plugin.domain.model.BuiltinPluginDefinition;
import com.pydlove.regent.plugin.domain.port.BuiltinPluginCatalog;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/** 从 classpath 的 {@code builtin-plugins/} 读取随 JAR 发布的内置插件。 */
public class ClasspathBuiltinPluginCatalog implements BuiltinPluginCatalog {

    private static final String MANIFEST_PATTERN =
            "classpath*:builtin-plugins/*/manifest.properties";

    private final ResourcePatternResolver resourceResolver;

    public ClasspathBuiltinPluginCatalog() {
        this(new PathMatchingResourcePatternResolver());
    }

    public ClasspathBuiltinPluginCatalog(ResourcePatternResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    @Override
    public List<BuiltinPluginDefinition> loadAll() {
        try {
            Resource[] manifests = resourceResolver.getResources(MANIFEST_PATTERN);
            if (manifests.length == 0) {
                throw new IllegalStateException(
                        "classpath 中没有找到 builtin-plugins/*/manifest.properties");
            }
            List<BuiltinPluginDefinition> definitions = new ArrayList<>(manifests.length);
            for (Resource manifest : manifests) {
                definitions.add(readManifest(manifest));
            }
            definitions.sort(Comparator.comparing(BuiltinPluginDefinition::key));
            return List.copyOf(definitions);
        } catch (IOException e) {
            throw new IllegalStateException("读取内置插件清单失败", e);
        }
    }

    private BuiltinPluginDefinition readManifest(Resource manifest) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(
                manifest.getInputStream(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        String location = manifest.getDescription();
        String contentFile = required(properties, "content-file", location);
        if (contentFile.contains("/") || contentFile.contains("\\") || contentFile.contains("..")) {
            throw new IllegalStateException(
                    "内置插件 content-file 只能是同目录文件名：" + location + " -> " + contentFile);
        }
        Resource contentResource = manifest.createRelative(contentFile);
        if (!contentResource.exists()) {
            throw new IllegalStateException(
                    "内置插件正文不存在：" + location + " -> " + contentFile);
        }
        String content;
        try (var input = contentResource.getInputStream()) {
            content = StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        }

        return new BuiltinPluginDefinition(
                required(properties, "key", location),
                required(properties, "name", location),
                optional(properties, "description"),
                parseTags(properties.getProperty("tags")),
                required(properties, "filename", location),
                content,
                parseEnabled(properties.getProperty("enabled", "true"), location));
    }

    private static String required(Properties properties, String key, String location) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "内置插件清单缺少 " + key + "：" + location);
        }
        return value.trim();
    }

    private static String optional(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split("[,，]"))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toList();
    }

    private static boolean parseEnabled(String value, String location) {
        String normalized = value.trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        throw new IllegalStateException(
                "内置插件 enabled 只能是 true 或 false：" + location + " -> " + value);
    }
}
