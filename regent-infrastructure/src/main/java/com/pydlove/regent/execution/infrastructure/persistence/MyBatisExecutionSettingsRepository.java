package com.pydlove.regent.execution.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.ExecutionSettings;
import com.pydlove.regent.execution.domain.repository.ExecutionSettingsRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/** 平台级执行设置的 MyBatis 实现。固定主键 1，查询未初始化时返回空。 */
@Repository
public class MyBatisExecutionSettingsRepository implements ExecutionSettingsRepository {

    private static final int SETTINGS_ID = 1;

    private final ExecutionSettingsMapper mapper;

    public MyBatisExecutionSettingsRepository(ExecutionSettingsMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ExecutionSettings> find() {
        return Optional.ofNullable(mapper.select())
                .map(row -> new ExecutionSettings(row.getMaxConcurrentTasks()));
    }

    @Override
    public void save(ExecutionSettings settings) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        mapper.upsert(SETTINGS_ID, settings.maxConcurrentTasks(),
                LocalDateTime.ofInstant(now, ZoneOffset.UTC));
    }
}
