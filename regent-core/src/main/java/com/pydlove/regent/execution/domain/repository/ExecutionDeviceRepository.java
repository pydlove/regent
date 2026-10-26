package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.ExecutionDevice;
import com.pydlove.regent.identity.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/** 执行设备心跳仓储。设备 ID 由客户端生成，平台按 ID 幂等覆盖最近一次心跳。 */
public interface ExecutionDeviceRepository {

    Optional<ExecutionDevice> findByDeviceId(String deviceId);

    List<ExecutionDevice> findByOwner(UserId ownerId);

    void save(ExecutionDevice device);
}
