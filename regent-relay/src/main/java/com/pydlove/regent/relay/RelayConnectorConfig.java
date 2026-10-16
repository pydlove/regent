package com.pydlove.regent.relay;

import java.net.URI;
import java.time.Duration;

record RelayConnectorConfig(
    URI serverBase,
    URI localBase,
    String sharedSecret,
    int connections,
    Duration pollWait,
    Duration requestTimeout
) {
    RelayConnectorConfig {
        validateBase(serverBase, "REGENT_RELAY_SERVER_URL");
        validateBase(localBase, "REGENT_RELAY_LOCAL_URL");
        RelayServerConfig.validateSecret(sharedSecret);
        if (connections < 1 || connections > 32) {
            throw new IllegalArgumentException("REGENT_RELAY_CONNECTIONS 必须在 1 到 32 之间");
        }
        if (pollWait.isNegative() || pollWait.isZero()) {
            throw new IllegalArgumentException("连接器长轮询时间必须大于 0");
        }
        if (requestTimeout.compareTo(pollWait) <= 0) {
            throw new IllegalArgumentException("连接器请求超时必须大于长轮询时间");
        }
    }

    private static void validateBase(URI uri, String name) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException(name + " 必须是绝对 HTTP(S) 地址");
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(name + " 只允许 http 或 https");
        }
    }
}
