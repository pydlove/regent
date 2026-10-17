package com.pydlove.regent.settings.application.command;

import com.pydlove.regent.settings.application.query.RelaySettingsView;
import com.pydlove.regent.settings.application.query.RelayState;
import com.pydlove.regent.settings.domain.model.RelaySettings;
import com.pydlove.regent.settings.domain.port.RelayHealthProbe;
import com.pydlove.regent.settings.domain.repository.RelaySettingsRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 中继设置的状态判断、持久化与探测失败回退。 */
class RelaySettingsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T02:30:00Z");

    private MemoryRepository repository;
    private RecordingProbe probe;
    private RelaySettingsService service;

    @BeforeEach
    void setUp() {
        repository = new MemoryRepository();
        probe = new RecordingProbe();
        service = new RelaySettingsService(
                repository,
                probe,
                RelaySettings.unconfigured(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("没有服务器地址时返回未配置且不发起网络请求")
    void reportsUnconfiguredWithoutProbing() {
        RelaySettingsView view = service.current();

        assertEquals(RelayState.UNCONFIGURED, view.status().state());
        assertNull(view.status().checkedAt());
        assertEquals(0, probe.calls);
    }

    @Test
    @DisplayName("已配置但关闭监听时返回停用状态")
    void reportsDisabledWhenMonitoringIsOff() {
        repository.settings = new RelaySettings("https://relay.example.com", false);

        RelaySettingsView view = service.current();

        assertEquals(RelayState.DISABLED, view.status().state());
        assertEquals(0, probe.calls);
    }

    @Test
    @DisplayName("服务器和连接器都在线时返回在线状态")
    void reportsOnline() {
        repository.settings = new RelaySettings("https://relay.example.com", true);
        probe.result = new RelayHealthProbe.ProbeResult(true, true, 3, 47, "正常");

        RelaySettingsView view = service.current();

        assertEquals(RelayState.ONLINE, view.status().state());
        assertEquals(Boolean.TRUE, view.status().connectorOnline());
        assertEquals(3, view.status().queueDepth());
        assertEquals(47L, view.status().latencyMillis());
        assertEquals(NOW, view.status().checkedAt());
        assertEquals("https://relay.example.com", probe.lastServerUrl);
    }

    @Test
    @DisplayName("服务器可达但连接器离线时返回降级而不是离线")
    void reportsDegradedWhenConnectorIsOffline() {
        repository.settings = new RelaySettings("https://relay.example.com", true);
        probe.result = new RelayHealthProbe.ProbeResult(true, false, 0, 12, "连接器离线");

        RelaySettingsView view = service.current();

        assertEquals(RelayState.DEGRADED, view.status().state());
        assertEquals(Boolean.FALSE, view.status().connectorOnline());
    }

    @Test
    @DisplayName("健康检查失败时返回离线并保留检查时间")
    void reportsOfflineWhenProbeFails() {
        repository.settings = new RelaySettings("https://relay.example.com", true);
        probe.result = new RelayHealthProbe.ProbeResult(false, false, 0, 3000, "无法连接");

        RelaySettingsView view = service.current();

        assertEquals(RelayState.OFFLINE, view.status().state());
        assertEquals(NOW, view.status().checkedAt());
    }

    @Test
    @DisplayName("探活适配器意外抛异常时也返回离线")
    void reportsOfflineWhenProbeThrows() {
        repository.settings = new RelaySettings("https://relay.example.com", true);
        probe.failure = new IllegalStateException("boom");

        RelaySettingsView view = service.current();

        assertEquals(RelayState.OFFLINE, view.status().state());
        assertEquals("中继健康检查失败", view.status().message());
    }

    @Test
    @DisplayName("保存设置时去掉地址两侧空白并立即返回新状态")
    void updatesAndNormalizesSettings() {
        probe.result = new RelayHealthProbe.ProbeResult(true, true, 0, 18, "正常");

        RelaySettingsView view = service.update("  https://relay.example.com/  ", true);

        assertEquals("https://relay.example.com/", repository.settings.serverUrl());
        assertTrue(repository.settings.monitoringEnabled());
        assertEquals(RelayState.ONLINE, view.status().state());
    }

    @Test
    @DisplayName("启用监听时不能使用空地址或带凭据的地址")
    void rejectsUnsafeOrIncompleteSettings() {
        assertThrows(DomainException.class, () -> service.update(" ", true));
        assertThrows(
                DomainException.class,
                () -> service.update("https://user:password@relay.example.com", true)
        );
        assertThrows(
                DomainException.class,
                () -> service.update("ftp://relay.example.com", true)
        );
        assertFalse(repository.saved);
    }

    private static final class MemoryRepository implements RelaySettingsRepository {

        private RelaySettings settings;
        private boolean saved;

        @Override
        public Optional<RelaySettings> find() {
            return Optional.ofNullable(settings);
        }

        @Override
        public void save(RelaySettings settings) {
            this.settings = settings;
            this.saved = true;
        }
    }

    private static final class RecordingProbe implements RelayHealthProbe {

        private ProbeResult result = new ProbeResult(true, true, 0, 1, "正常");
        private RuntimeException failure;
        private String lastServerUrl;
        private int calls;

        @Override
        public ProbeResult probe(String serverUrl) {
            calls++;
            lastServerUrl = serverUrl;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
