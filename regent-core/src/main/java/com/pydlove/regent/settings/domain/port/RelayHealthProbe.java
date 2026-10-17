package com.pydlove.regent.settings.domain.port;

/**
 * 读取中继服务器公开健康状态的端口。
 *
 * <p>探活失败是正常业务状态，不抛异常；适配器把网络、超时与响应格式问题都翻译成
 * {@link ProbeResult}，界面因此能区分服务器不可达与连接器离线。
 */
public interface RelayHealthProbe {

    ProbeResult probe(String serverUrl);

    record ProbeResult(
            boolean reachable,
            boolean connectorOnline,
            int queueDepth,
            long latencyMillis,
            String message
    ) {
        public ProbeResult {
            if (queueDepth < 0 || latencyMillis < 0) {
                throw new IllegalArgumentException("中继队列深度与延迟不能为负数");
            }
            message = message == null ? "" : message;
        }
    }
}
