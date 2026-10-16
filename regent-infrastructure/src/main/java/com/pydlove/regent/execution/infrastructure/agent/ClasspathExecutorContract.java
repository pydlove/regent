package com.pydlove.regent.execution.infrastructure.agent;

import com.pydlove.regent.execution.domain.port.ExecutorContract;
import com.pydlove.regent.execution.infrastructure.codex.SchemaFileProvider;
import com.pydlove.regent.shared.domain.DomainException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 契约文本的 classpath 实现。
 *
 * <p>三段文本各有各的读者：{@code agent-protocol.md} 给外部执行者（怎么认领、怎么上报），
 * {@code platform-contract.md} 给所有执行者（三条结局、定级尺度、硬性要求），
 * 出参 schema 给两边的校验。它们一起打进 jar，改一次就同时改掉内部与外部两条路的说法 ——
 * 这正是把它们放在 classpath 而不是文档目录里的理由。
 *
 * <p>{@code platform-contract.md} 与出参 schema 复用 {@link SchemaFileProvider}：
 * 内部执行器读的也是这两份，多一个读取点就多一次「两份文本悄悄分叉」的机会。
 */
public class ClasspathExecutorContract implements ExecutorContract {

    private static final String PROTOCOL_RESOURCE = "/agent/agent-protocol.md";

    private final SchemaFileProvider schemaFileProvider;

    public ClasspathExecutorContract(SchemaFileProvider schemaFileProvider) {
        this.schemaFileProvider = schemaFileProvider;
    }

    @Override
    public String reportingRules() {
        return schemaFileProvider.contract();
    }

    @Override
    public String agentProtocol() {
        return read(PROTOCOL_RESOURCE);
    }

    @Override
    public String resultSchemaJson() {
        return schemaFileProvider.schemaJson();
    }

    private static String read(String resource) {
        try (InputStream in = ClasspathExecutorContract.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new DomainException("协议文件不在 classpath 里：" + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DomainException("协议文件读取失败：" + resource, e);
        }
    }
}
