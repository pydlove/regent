package com.pydlove.regent.execution.application.command;

/** 设备代理换进程实例时的恢复入口；实现方同时负责释放进程内执行位。 */
@FunctionalInterface
public interface DeviceRestartRecoveryHandler {

    int recover(String deviceId, String reason);
}
