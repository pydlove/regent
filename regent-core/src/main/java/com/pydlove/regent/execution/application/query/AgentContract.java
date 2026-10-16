package com.pydlove.regent.execution.application.query;

/**
 * 平台对外公布的「怎么干活」。
 *
 * @param protocol   协议版本
 * @param markdown   协议全文（含上报约定），连上来的工具读这一份就够
 * @param schemaJson 出参 JSON Schema 原文
 */
public record AgentContract(String protocol, String markdown, String schemaJson) {
}
