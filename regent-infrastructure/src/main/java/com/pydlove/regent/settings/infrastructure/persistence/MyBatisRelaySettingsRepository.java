package com.pydlove.regent.settings.infrastructure.persistence;

import com.pydlove.regent.settings.domain.model.RelaySettings;
import com.pydlove.regent.settings.domain.repository.RelaySettingsRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/** 中继设置单行表的仓储实现。 */
public class MyBatisRelaySettingsRepository implements RelaySettingsRepository {

    private static final int SETTINGS_ID = 1;

    private final RelaySettingsMapper mapper;

    public MyBatisRelaySettingsRepository(RelaySettingsMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<RelaySettings> find() {
        return Optional.ofNullable(mapper.select())
                .map(row -> new RelaySettings(row.getServerUrl(), row.getMonitoringEnabled()));
    }

    @Override
    public void save(RelaySettings settings) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        mapper.upsert(
                SETTINGS_ID,
                settings.serverUrl(),
                settings.monitoringEnabled(),
                LocalDateTime.ofInstant(now, ZoneOffset.UTC)
        );
    }
}
