package com.pydlove.regent.boot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.settings.application.command.RelaySettingsService;
import com.pydlove.regent.settings.domain.model.RelaySettings;
import com.pydlove.regent.settings.domain.port.RelayHealthProbe;
import com.pydlove.regent.settings.domain.repository.RelaySettingsRepository;
import com.pydlove.regent.settings.infrastructure.persistence.MyBatisRelaySettingsRepository;
import com.pydlove.regent.settings.infrastructure.persistence.RelaySettingsMapper;
import com.pydlove.regent.settings.infrastructure.relay.HttpRelayHealthProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/** 平台设置上下文的显式装配。 */
@Configuration
public class SettingsConfig {

    @Bean
    public RelaySettingsRepository relaySettingsRepository(RelaySettingsMapper mapper) {
        return new MyBatisRelaySettingsRepository(mapper);
    }

    @Bean
    public RelayHealthProbe relayHealthProbe(
            ObjectMapper objectMapper,
            @Value("${regent.relay.monitor.request-timeout:PT3S}") Duration requestTimeout) {
        return new HttpRelayHealthProbe(objectMapper, requestTimeout);
    }

    @Bean
    public RelaySettingsService relaySettingsService(
            RelaySettingsRepository repository,
            RelayHealthProbe healthProbe,
            @Value("${regent.relay.monitor.server-url:}") String defaultServerUrl,
            @Value("${regent.relay.monitor.enabled:true}") boolean monitoringEnabled) {
        String normalizedUrl = defaultServerUrl == null ? "" : defaultServerUrl.trim();
        RelaySettings defaults = new RelaySettings(
                normalizedUrl,
                monitoringEnabled && !normalizedUrl.isEmpty()
        );
        return new RelaySettingsService(repository, healthProbe, defaults, Clock.systemUTC());
    }
}
