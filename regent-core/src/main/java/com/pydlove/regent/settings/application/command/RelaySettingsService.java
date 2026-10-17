package com.pydlove.regent.settings.application.command;

import com.pydlove.regent.settings.application.query.RelaySettingsView;
import com.pydlove.regent.settings.application.query.RelayState;
import com.pydlove.regent.settings.application.query.RelayStatusView;
import com.pydlove.regent.settings.domain.model.RelaySettings;
import com.pydlove.regent.settings.domain.port.RelayHealthProbe;
import com.pydlove.regent.settings.domain.repository.RelaySettingsRepository;

import java.time.Clock;
import java.time.Instant;

/**
 * 中继设置读写与实时探活。
 *
 * <p>每次读取都返回当前状态，而不是依赖一个可能已经失真的后台缓存。界面只在设置页可见时轮询，
 * 远端请求由基础设施适配器设置短超时，避免一次失联拖住整个请求。
 */
public class RelaySettingsService {

    private final RelaySettingsRepository repository;
    private final RelayHealthProbe healthProbe;
    private final RelaySettings defaults;
    private final Clock clock;

    public RelaySettingsService(RelaySettingsRepository repository,
                                RelayHealthProbe healthProbe,
                                RelaySettings defaults,
                                Clock clock) {
        this.repository = repository;
        this.healthProbe = healthProbe;
        this.defaults = defaults;
        this.clock = clock;
    }

    public RelaySettingsView current() {
        return view(settings());
    }

    public RelaySettingsView update(String serverUrl, boolean monitoringEnabled) {
        RelaySettings settings = new RelaySettings(serverUrl, monitoringEnabled);
        repository.save(settings);
        return view(settings);
    }

    private RelaySettings settings() {
        return repository.find().orElse(defaults);
    }

    private RelaySettingsView view(RelaySettings settings) {
        return new RelaySettingsView(
                settings.serverUrl(),
                settings.monitoringEnabled(),
                status(settings)
        );
    }

    private RelayStatusView status(RelaySettings settings) {
        if (!settings.monitoringEnabled()) {
            return new RelayStatusView(
                    settings.configured() ? RelayState.DISABLED : RelayState.UNCONFIGURED,
                    null,
                    null,
                    null,
                    null,
                    settings.configured() ? "中继状态监听已停用" : "尚未设置中继服务器"
            );
        }

        RelayHealthProbe.ProbeResult result;
        try {
            result = healthProbe.probe(settings.serverUrl());
        } catch (RuntimeException exception) {
            result = new RelayHealthProbe.ProbeResult(false, false, 0, 0, "中继健康检查失败");
        }

        RelayState state;
        if (!result.reachable()) {
            state = RelayState.OFFLINE;
        } else if (!result.connectorOnline()) {
            state = RelayState.DEGRADED;
        } else {
            state = RelayState.ONLINE;
        }

        return new RelayStatusView(
                state,
                result.connectorOnline(),
                result.queueDepth(),
                result.latencyMillis(),
                Instant.now(clock),
                result.message()
        );
    }
}
