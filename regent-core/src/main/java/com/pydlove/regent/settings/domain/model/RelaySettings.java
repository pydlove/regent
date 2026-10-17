package com.pydlove.regent.settings.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 中继服务器状态监听的平台设置。
 *
 * <p>这里只保存可公开的服务器地址。连接器共享密钥仍由本机部署配置维护，
 * 不进入领域对象、数据库或浏览器。
 */
public record RelaySettings(String serverUrl, boolean monitoringEnabled) {

    public static final int MAX_SERVER_URL_LENGTH = 2048;

    public RelaySettings {
        serverUrl = serverUrl == null ? "" : serverUrl.trim();
        if (serverUrl.length() > MAX_SERVER_URL_LENGTH) {
            throw new DomainException("中继服务器地址不能超过 " + MAX_SERVER_URL_LENGTH + " 个字符");
        }
        if (serverUrl.isEmpty()) {
            if (monitoringEnabled) {
                throw new DomainException("启用中继状态监听前必须填写服务器地址");
            }
        } else {
            validateServerUrl(serverUrl);
        }
    }

    public static RelaySettings unconfigured() {
        return new RelaySettings("", false);
    }

    public boolean configured() {
        return !serverUrl.isEmpty();
    }

    private static void validateServerUrl(String value) {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw new DomainException("中继服务器地址格式不正确", exception);
        }
        if (!uri.isAbsolute() || uri.getHost() == null) {
            throw new DomainException("中继服务器地址必须是绝对 HTTP(S) 地址");
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new DomainException("中继服务器地址只允许 http 或 https");
        }
        if (uri.getUserInfo() != null) {
            throw new DomainException("中继服务器地址不能包含用户名或密码");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new DomainException("中继服务器地址不能包含查询参数或片段");
        }
        if (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath())) {
            throw new DomainException("中继服务器地址必须指向站点根地址");
        }
    }
}
