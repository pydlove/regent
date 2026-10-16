package com.pydlove.regent.execution.infrastructure.codex;

import com.pydlove.regent.shared.domain.DomainException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 平台出参契约的提供者：把 classpath 里的 JSON Schema 与上报约定落到运行目录。
 *
 * <p>{@code codex exec --output-schema} 只接受<b>文件路径</b>，不接受内联 schema，
 * 所以必须落一次盘。落在 {@code ~/.regent/} 下而不是仓库里：运行时产物一律不进仓库，
 * 否则每次构建都会在 {@code git status} 里留下一堆噪音。
 *
 * <p>内容不一致才重写：这个文件在每次执行前都会被读一次，无脑覆盖会让磁盘
 * 每跑一次任务就写一遍同样的字节。
 */
public class SchemaFileProvider {

    private static final String SCHEMA_RESOURCE = "/codex/codex-exec-result.schema.json";
    private static final String CONTRACT_RESOURCE = "/codex/platform-contract.md";
    private static final String SCHEMA_FILE_NAME = "codex-exec-result.schema.json";

    private final Path directory;
    private final AtomicReference<Path> materialized = new AtomicReference<>();
    private final AtomicReference<String> contract = new AtomicReference<>();

    public SchemaFileProvider(Path directory) {
        this.directory = directory;
    }

    /** schema 文件路径，首次调用时落盘。 */
    public Path schemaFile() {
        Path cached = materialized.get();
        if (cached != null) {
            return cached;
        }
        String content = readResource(SCHEMA_RESOURCE);
        Path target = directory.resolve(SCHEMA_FILE_NAME);
        try {
            Files.createDirectories(directory);
            if (!Files.exists(target) || !content.equals(Files.readString(target, StandardCharsets.UTF_8))) {
                Files.writeString(target, content, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new DomainException("出参 schema 无法落盘：" + target, e);
        }
        materialized.set(target);
        return target;
    }

    /** 平台上报约定。追加在任务指令之后，告诉模型该按什么规矩汇报。 */
    public String contract() {
        String cached = contract.get();
        if (cached != null) {
            return cached;
        }
        String content = readResource(CONTRACT_RESOURCE);
        contract.set(content);
        return content;
    }

    private static String readResource(String resource) {
        try (InputStream in = SchemaFileProvider.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new DomainException("约定文件不在 classpath 里：" + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DomainException("约定文件读取失败：" + resource, e);
        }
    }
}
