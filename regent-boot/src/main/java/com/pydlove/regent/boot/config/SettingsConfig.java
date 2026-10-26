package com.pydlove.regent.boot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.settings.application.command.RelaySettingsService;
import com.pydlove.regent.settings.application.command.GitCommitSettingsService;
import com.pydlove.regent.settings.domain.repository.GitCommitSettingsRepository;
import com.pydlove.regent.settings.domain.model.RelaySettings;
import com.pydlove.regent.settings.domain.port.RelayHealthProbe;
import com.pydlove.regent.settings.domain.port.RelayInstaller;
import com.pydlove.regent.settings.domain.repository.RelaySettingsRepository;
import com.pydlove.regent.settings.infrastructure.persistence.MyBatisRelaySettingsRepository;
import com.pydlove.regent.settings.infrastructure.persistence.GitCommitSettingsMapper;
import com.pydlove.regent.settings.infrastructure.persistence.MyBatisGitCommitSettingsRepository;
import com.pydlove.regent.settings.infrastructure.persistence.RelaySettingsMapper;
import com.pydlove.regent.settings.infrastructure.relay.HttpRelayHealthProbe;
import com.pydlove.regent.settings.infrastructure.relay.ShellRelayInstaller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.nio.file.Path;

/** 平台设置上下文的显式装配。 */
@Configuration
public class SettingsConfig {

    @Bean
    public RelaySettingsRepository relaySettingsRepository(RelaySettingsMapper mapper) {
        return new MyBatisRelaySettingsRepository(mapper);
    }

    @Bean
    public GitCommitSettingsRepository gitCommitSettingsRepository(GitCommitSettingsMapper mapper) {
        return new MyBatisGitCommitSettingsRepository(mapper);
    }

    @Bean
    public GitCommitSettingsService gitCommitSettingsService(
            GitCommitSettingsRepository repository) {
        return new GitCommitSettingsService(repository);
    }

    @Bean
    public RelayHealthProbe relayHealthProbe(
            ObjectMapper objectMapper,
            @Value("${regent.relay.monitor.request-timeout:PT3S}") Duration requestTimeout) {
        return new HttpRelayHealthProbe(objectMapper, requestTimeout);
    }

    @Bean
    public RelayInstaller relayInstaller(
            @Value("${regent.relay.install.script:deploy/remote/deploy.sh}") String script,
            @Value("${regent.relay.install.timeout:PT15M}") Duration timeout) {
        return new ShellRelayInstaller(Path.of(script), timeout);
    }

    @Bean
    public RelaySettingsService relaySettingsService(
            RelaySettingsRepository repository,
            RelayHealthProbe healthProbe,
            RelayInstaller installer,
            @Value("${regent.relay.monitor.server-url:}") String defaultServerUrl,
            @Value("${regent.relay.monitor.enabled:true}") boolean monitoringEnabled) {
        String normalizedUrl = defaultServerUrl == null ? "" : defaultServerUrl.trim();
        RelaySettings defaults = new RelaySettings(
                normalizedUrl,
                monitoringEnabled && !normalizedUrl.isEmpty()
        );
        return new RelaySettingsService(
                repository,
                healthProbe,
                installer,
                defaults,
                Clock.systemUTC()
        );
    }
}
