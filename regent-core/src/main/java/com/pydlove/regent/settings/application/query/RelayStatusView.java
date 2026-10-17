package com.pydlove.regent.settings.application.query;

import java.time.Instant;

/** 中继服务器与本地连接器的当前状态。 */
public record RelayStatusView(
        RelayState state,
        Boolean connectorOnline,
        Integer queueDepth,
        Long latencyMillis,
        Instant checkedAt,
        String message
) {
}
