package com.pydlove.regent.execution.application.query;

import com.pydlove.regent.execution.application.command.AgentRunService;
import com.pydlove.regent.execution.domain.port.ExecutorContract;

/**
 * 契约查询 —— {@code GET /api/agent/contract} 背后那一步。
 *
 * <p>存在的理由只有一个：接口层不许直接依赖 infrastructure，所以「文本从哪来」必须走端口。
 * 全文由两段拼成：外部流程说明（怎么认领、怎么上报、批复时干什么）加上平台的上报约定
 * （三条结局、定级尺度、硬性要求）—— 后者与内部执行器读到的是同一份，不复制，防止两边说法分叉。
 */
public class AgentContractService {

    private final ExecutorContract contract;

    public AgentContractService(ExecutorContract contract) {
        this.contract = contract;
    }

    public AgentContract current() {
        return new AgentContract(AgentRunService.PROTOCOL,
                contract.agentProtocol() + "\n\n---\n\n" + contract.reportingRules(),
                contract.resultSchemaJson());
    }
}
