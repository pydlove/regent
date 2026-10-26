package com.pydlove.regent.execution.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 执行设备心跳的 MyBatis 映射接口。 */
public interface ExecutionDeviceMapper {

    ExecutionDeviceData selectByDeviceId(@Param("deviceId") String deviceId);

    List<ExecutionDeviceData> selectByOwner(@Param("ownerId") long ownerId);

    int upsert(ExecutionDeviceData data);
}
