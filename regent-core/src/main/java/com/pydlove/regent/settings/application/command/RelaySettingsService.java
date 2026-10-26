package com.pydlove.regent.settings.application.command;

import com.pydlove.regent.settings.application.query.RelaySettingsView;
import com.pydlove.regent.settings.application.query.RelayInstallView;
import com.pydlove.regent.settings.application.query.RelayState;
import com.pydlove.regent.settings.application.query.RelayStatusView;
import com.pydlove.regent.settings.domain.model.RelaySettings;
import com.pydlove.regent.settings.domain.port.RelayHealthProbe;
import com.pydlove.regent.settings.domain.port.RelayInstaller;
import com.pydlove.regent.settings.domain.repository.RelaySettingsRepository;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 中继设置读写与实时探活。
 *
 * <p>每次读取都返回当前状态，而不是依赖一个可能已经失真的后台缓存。界面只在设置页可见时轮询，
 * 远端请求由基础设施适配器设置短超时，避免一次失联拖住整个请求。
 */
public class RelaySettingsService {

    private static final int MAX_PASSWORD_LENGTH = 4096;

    private final RelaySettingsRepository repository;
    private final RelayHealthProbe healthProbe;
    private final RelayInstaller installer;
    private final RelaySettings defaults;
    private final Clock clock;
    private final AtomicBoolean installInProgress = new AtomicBoolean();

    public RelaySettingsService(RelaySettingsRepository repository,
                                RelayHealthProbe healthProbe,
                                RelayInstaller installer,
                                RelaySettings defaults,
                                Clock clock) {
        this.repository = repository;
        this.healthProbe = healthProbe;
        this.installer = installer;
        this.defaults = defaults;
        this.clock = clock;
    }

    public RelaySettingsView current() {
        return view(settings());
    }

    public RelaySettingsView update(String serverUrl,
                                    boolean monitoringEnabled,
                                    String serverIp,
                                    String sshUsername,
                                    String installDirectory) {
        RelaySettings settings = new RelaySettings(
                serverUrl,
                monitoringEnabled,
                serverIp,
                sshUsername,
                installDirectory
        );
        repository.save(settings);
        return view(settings);
    }

    public RelayInstallView install(String password) {
        RelaySettings settings = settings();
        if (!settings.installConfigured()) {
            throw new DomainException("安装中继前必须填写服务器域名或 IP");
        }
        if (password != null && password.length() > MAX_PASSWORD_LENGTH) {
            throw new DomainException("SSH 密码长度超出限制");
        }
        if (!installInProgress.compareAndSet(false, true)) {
            throw new DomainException("中继正在安装，请等待当前任务完成");
        }
        try {
            RelayInstaller.InstallResult result = installer.install(
                    new RelayInstaller.InstallRequest(
                            settings.serverIp(),
                            settings.serverUrl(),
                            settings.sshUsername(),
                            password == null ? "" : password,
                            settings.installDirectory()
                    )
            );
            return new RelayInstallView(
                    result.success(),
                    result.installDirectory(),
                    result.message()
            );
        } finally {
            installInProgress.set(false);
        }
    }

    private RelaySettings settings() {
        return repository.find().orElse(defaults);
    }

    private RelaySettingsView view(RelaySettings settings) {
        return new RelaySettingsView(
                settings.serverUrl(),
                settings.monitoringEnabled(),
                settings.serverIp(),
                settings.sshUsername(),
                settings.installDirectory(),
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
