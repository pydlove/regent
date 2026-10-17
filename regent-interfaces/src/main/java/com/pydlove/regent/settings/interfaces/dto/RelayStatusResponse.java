package com.pydlove.regent.settings.interfaces.dto;

import com.pydlove.regent.settings.application.query.RelayState;
import com.pydlove.regent.settings.application.query.RelayStatusView;

import java.time.Instant;

/** 中继服务器与连接器的界面状态。 */
public record RelayStatusResponse(
        RelayState state,
        Boolean connectorOnline,
        Integer queueDepth,
        Long latencyMillis,
        Instant checkedAt,
        String message
) {
    public static RelayStatusResponse from(RelayStatusView view) {
        return new RelayStatusResponse(
                view.state(),
                view.connectorOnline(),
                view.queueDepth(),
                view.latencyMillis(),
                view.checkedAt(),
                view.message()
        );
    }
}
