package com.pydlove.regent.execution.domain.port;

/**
 * 执行事件的回调。
 *
 * <p>为什么不把「序号」交给执行器：一次尝试里可能有多轮执行（自动决断后立刻续跑），
 * 序号必须在整次尝试内连续，而执行器只看得见自己那一轮。把它交给执行器，
 * 第二轮就会从 0 重新开始，直接撞上 {@code uk_event_run_seq} 唯一键。
 *
 * <p>实现必须容忍回调被高频调用（每一行输出一次），并且不能在里面抛异常 ——
 * 抛出去会中断事件读取，进程的输出管道填满后 codex 会卡住。
 */
@FunctionalInterface
public interface RunEventListener {

    void onEvent(String type, String payloadJson);
}
