package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.ExecutionDevice;

import java.util.List;
import java.util.Objects;

/** 设备当前状态与本次心跳可取走的任务。 */
public record DevicePresence(ExecutionDevice device, boolean online, List<Long> taskIds) {

    public DevicePresence {
        Objects.requireNonNull(device, "设备");
        taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
    }
}
