package com.pydlove.regent.execution.domain.port;

/**
 * 平台对外承诺的「怎么干活」—— 两份文本，一段 schema。
 *
 * <p>它们是平台与执行器之间的契约，而不是某个适配器的私产：内部执行器（{@code codex exec}）
 * 靠 {@link #reportingRules()} 知道该按什么规矩汇报，外部执行者（HTTP 那条路）读
 * {@link #agentProtocol()} + {@link #reportingRules()} 知道整件事该怎么做。
 *
 * <p>因此这里必须是一个端口而不是「基础设施自己去读 classpath」：接口层要能把全文吐给
 * 连上来的工具，而接口层不许直接依赖 infrastructure（ArchUnit 会拦）。文本从哪来
 * （classpath、数据库、远端配置）是适配器的自由。
 */
public interface ExecutorContract {

    /**
     * 出参约定：三条结局、定级的尺度、硬性要求。内部执行器把它追加在任务指令之后。
     */
    String reportingRules();

    /**
     * 外部执行者协议全文：怎么认领、怎么心跳、怎么上报、等批复时干什么。
     */
    String agentProtocol();

    /** 出参 JSON Schema 原文。字段一个都不能少、也不能多，所以要把原文给出去而不是转述。 */
    String resultSchemaJson();
}
