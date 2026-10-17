package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.application.command.AgentWorkPackage;

import java.time.Instant;
import java.util.List;

/**
 * 工作包响应。字段与 {@link AgentWorkPackage} 一一对应 —— 这一层不做任何加工，
 * 因为它是协议的一部分：外部执行者按这些字段名写代码，改一个名字就是改协议。
 */
public record AgentTaskClaimResponse(String protocol,
                                     long taskId,
                                     long runId,
                                     int attemptNo,
                                     String title,
                                     String projectCode,
                                     String workdir,
                                     String branch,
                                     String instruction,
                                     String sessionId,
                                     List<Image> images,
                                     List<Plugin> plugins,
                                     List<EnvironmentVariable> environment,
                                     String decisionThreshold,
                                     int leaseSeconds,
                                     Instant leaseExpiresAt,
                                     String contractUrl) {

    public static AgentTaskClaimResponse from(AgentWorkPackage work) {
        return new AgentTaskClaimResponse(work.protocol(), work.taskId(), work.runId(), work.attemptNo(),
                work.title(), work.projectCode(), work.workdir(), work.branch(), work.instruction(),
                work.sessionId(),
                work.images().stream().map(image -> new Image(image.id(), image.filename(), image.path())).toList(),
                work.plugins().stream().map(plugin -> new Plugin(
                        plugin.id(), plugin.name(), plugin.revision(), plugin.filename(),
                        plugin.contentHash(), plugin.content())).toList(),
                work.environment().stream().map(variable -> new EnvironmentVariable(
                        variable.key(), variable.value(), variable.scope(),
                        variable.tags(), variable.description())).toList(),
                work.decisionThreshold(), work.leaseSeconds(), work.leaseExpiresAt(), work.contractUrl());
    }

    public record Image(long id, String filename, String path) {
    }

    public record Plugin(long id,
                         String name,
                         int revision,
                         String filename,
                         String contentHash,
                         String content) {
    }

    public record EnvironmentVariable(String key,
                                      String value,
                                      String scope,
                                      List<String> tags,
                                      String description) {
    }
}
