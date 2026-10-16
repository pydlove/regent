package com.pydlove.regent.relay;

import java.time.Duration;

record RelayServerConfig(
    String bindAddress,
    int port,
    String sharedSecret,
    Duration connectorPollWait,
    Duration requestTimeout,
    Duration connectorOfflineAfter,
    int queueCapacity,
    int maxBodyBytes
) {
    RelayServerConfig {
        if (bindAddress == null || bindAddress.isBlank()) {
            throw new IllegalArgumentException("REGENT_RELAY_BIND 不能为空");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("REGENT_RELAY_PORT 不合法");
        }
        validateSecret(sharedSecret);
        if (connectorPollWait.isNegative() || connectorPollWait.isZero()) {
            throw new IllegalArgumentException("连接器长轮询时间必须大于 0");
        }
        if (requestTimeout.compareTo(connectorPollWait) <= 0) {
            throw new IllegalArgumentException("公网请求超时必须大于连接器长轮询时间");
        }
        if (connectorOfflineAfter.compareTo(connectorPollWait) <= 0) {
            throw new IllegalArgumentException("连接器离线判定时间必须大于长轮询时间");
        }
        if (queueCapacity < 1 || maxBodyBytes < 1) {
            throw new IllegalArgumentException("队列容量与请求体上限必须大于 0");
        }
    }

    static void validateSecret(String secret) {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("REGENT_RELAY_SHARED_SECRET 至少需要 32 字节");
        }
    }
}
