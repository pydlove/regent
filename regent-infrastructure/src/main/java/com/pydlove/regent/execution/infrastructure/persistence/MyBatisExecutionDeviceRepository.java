package com.pydlove.regent.execution.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.ExecutionDevice;
import com.pydlove.regent.execution.domain.repository.ExecutionDeviceRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 执行设备心跳仓储。 */
@Repository
public class MyBatisExecutionDeviceRepository implements ExecutionDeviceRepository {

    private final ExecutionDeviceMapper mapper;

    public MyBatisExecutionDeviceRepository(ExecutionDeviceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ExecutionDevice> findByDeviceId(String deviceId) {
        return Optional.ofNullable(mapper.selectByDeviceId(deviceId))
                .map(ExecutionDeviceDataMapper::toDomain);
    }

    @Override
    public List<ExecutionDevice> findByOwner(UserId ownerId) {
        return mapper.selectByOwner(ownerId.value()).stream()
                .map(ExecutionDeviceDataMapper::toDomain)
                .toList();
    }

    @Override
    public void save(ExecutionDevice device) {
        mapper.upsert(ExecutionDeviceDataMapper.toData(device));
    }
}
