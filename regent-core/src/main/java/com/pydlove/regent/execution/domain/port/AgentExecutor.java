package com.pydlove.regent.execution.domain.port;

import com.pydlove.regent.execution.domain.model.ExecRequest;
import com.pydlove.regent.execution.domain.model.ExecResult;

/**
 * 执行器端口 —— 平台与「把活干了的那个东西」之间唯一的接口。
 *
 * <p>它刻意不认识 codex：命令行长什么样、事件流是什么格式、结构化出参写在哪个文件里，
 * 全是适配器的事。这样换执行器（另一个 CLI、一个 HTTP 服务、一个本地模型）时，
 * {@code regent-core} 一行不动，也解释了为什么这里的方法签名里没有任何
 * {@code Path}、{@code Process}、{@code JSON} 类型。
 *
 * <p>阻塞语义：本方法是<b>同步</b>的，返回即代表这次执行已经结束（成功 / 失败 / 阻塞 / 超时）。
 * 调用方负责把它放到工作线程上跑 —— 一次真实的执行可能持续几十分钟。
 */
public interface AgentExecutor {

    /**
     * 执行一次。新会话与断点续跑走同一个方法，靠 {@link ExecRequest#isResume()} 区分。
     *
     * @param listener 事件回调，每读到一条就回调一次
     */
    ExecResult execute(ExecRequest request, RunEventListener listener);
}
