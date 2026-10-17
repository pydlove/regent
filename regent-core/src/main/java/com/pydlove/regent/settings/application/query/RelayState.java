package com.pydlove.regent.settings.application.query;

/** 中继状态。服务器可达与连接器在线分开判断，避免把隧道故障误报成服务器宕机。 */
public enum RelayState {
    UNCONFIGURED,
    DISABLED,
    ONLINE,
    DEGRADED,
    OFFLINE
}
