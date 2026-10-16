package com.pydlove.regent.execution.interfaces.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.execution.application.query.AgentContract;
import com.pydlove.regent.shared.domain.DomainException;

import java.util.List;

/**
 * 契约响应 —— 「连上这个平台该怎么干活」的机器可读版。
 *
 * <p>{@code contract} 是给人（和模型）读的协议全文，{@code resultSchema} 是给程序校验的出参 Schema，
 * 中间两张表是索引：接口清单与决策分级。一个工具读完这一次请求，就不该再问平台任何「我该做什么」。
 *
 * <p>端点表与分级表写在这里而不是从注解里反射出来：它们是**对外承诺**，改动必须是有意识的 ——
 * 反射出来的表会随着重构悄悄变，而外部工具是照着表写死的。
 */
public record AgentContractResponse(String protocol,
                                    String platform,
                                    List<Endpoint> endpoints,
                                    List<Level> levels,
                                    JsonNode resultSchema,
                                    String contract) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static AgentContractResponse from(AgentContract contract) {
        return new AgentContractResponse(contract.protocol(), "Regent AI 任务执行平台",
                endpointTable(), levelTable(), schema(contract.schemaJson()), contract.markdown());
    }

    private static List<Endpoint> endpointTable() {
        return List.of(
                new Endpoint("GET", "/api/agent/contract", "协议全文 + 出参 schema + 端点表（就是本响应）"),
                new Endpoint("GET", "/api/agent/contract.md", "同一份协议的纯文本，返回 text/markdown"),
                new Endpoint("GET", "/api/agent/tasks?project={code}",
                        "可领任务清单。先看有哪些活，再决定领哪一条"),
                new Endpoint("POST", "/api/agent/tasks/claim",
                        "领一条任务。projectCode 限定项目，taskId 表示续跑指定任务；没有可领的活返回 204"),
                new Endpoint("POST", "/api/agent/runs/{runId}/heartbeat", "续租 + 取回 nextAction"),
                new Endpoint("POST", "/api/agent/runs/{runId}/report", "上报这一轮的结果（出参 JSON）"));
    }

    private static List<Level> levelTable() {
        return List.of(
                new Level("L0", "改文案、补日志、加注释、纯文档", "平台直接放行并留痕"),
                new Level("L1", "命名、内部结构、补测试、错误提示措辞", "平台按项目阈值放行并留痕"),
                new Level("L2", "接口签名变更、库表字段增减、跨模块改动、新增依赖", "挂起等人批准"),
                new Level("L3", "删数据删表、权限鉴权、发布与打包流程、密钥证书", "挂起并强提醒"));
    }

    private static JsonNode schema(String schemaJson) {
        try {
            return JSON.readTree(schemaJson);
        } catch (Exception e) {
            throw new DomainException("出参 schema 不是合法 JSON，无法对外公布", e);
        }
    }

    public record Endpoint(String method, String path, String summary) {
    }

    public record Level(String level, String scope, String handling) {
    }
}
