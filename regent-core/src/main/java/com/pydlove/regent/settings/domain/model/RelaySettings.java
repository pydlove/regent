package com.pydlove.regent.settings.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 中继服务器的连接、安装与状态监听设置。
 *
 * <p>SSH 密码与连接器共享密钥都不进入这个对象：密码只随安装命令使用一次，
 * 共享密钥仍由部署脚本写入运行时目录。
 */
public record RelaySettings(
        String serverUrl,
        boolean monitoringEnabled,
        String serverIp,
        String sshUsername,
        String installDirectory
) {

    public static final int MAX_SERVER_URL_LENGTH = 2048;
    public static final int MAX_SERVER_IP_LENGTH = 255;
    public static final int MAX_SSH_USERNAME_LENGTH = 32;
    public static final int MAX_INSTALL_DIRECTORY_LENGTH = 512;
    public static final String DEFAULT_SSH_USERNAME = "root";
    public static final String DEFAULT_INSTALL_DIRECTORY = "/root/app/regent-relay";

    public RelaySettings {
        serverUrl = normalizeServerUrl(serverUrl);
        serverIp = serverIp == null ? "" : serverIp.trim();
        sshUsername = sshUsername == null || sshUsername.isBlank()
                ? DEFAULT_SSH_USERNAME
                : sshUsername.trim();
        installDirectory = installDirectory == null || installDirectory.isBlank()
                ? DEFAULT_INSTALL_DIRECTORY
                : installDirectory.trim();
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
        validateServerIp(serverIp);
        validateSshUsername(sshUsername);
        validateInstallDirectory(installDirectory);
    }

    public RelaySettings(String serverUrl, boolean monitoringEnabled) {
        this(
                serverUrl,
                monitoringEnabled,
                "",
                DEFAULT_SSH_USERNAME,
                DEFAULT_INSTALL_DIRECTORY
        );
    }

    public static RelaySettings unconfigured() {
        return new RelaySettings("", false, "", DEFAULT_SSH_USERNAME, DEFAULT_INSTALL_DIRECTORY);
    }

    public boolean configured() {
        return !serverUrl.isEmpty();
    }

    public boolean installConfigured() {
        return !serverIp.isEmpty();
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
        String path = uri.getPath();
        if (path != null
                && !path.isEmpty()
                && (!path.startsWith("/")
                || !path.matches("/[A-Za-z0-9._~/-]*")
                || path.contains("//")
                || path.endsWith("/.")
                || path.endsWith("/..")
                || hasUnsafePathSegment(path))) {
            throw new DomainException("中继服务器地址只能包含安全的站点子路径");
        }
    }

    private static void validateServerIp(String value) {
        if (value.isEmpty()) {
            return;
        }
        if (value.length() > MAX_SERVER_IP_LENGTH) {
            throw new DomainException("服务器域名或 IP 不能超过 " + MAX_SERVER_IP_LENGTH + " 个字符");
        }
        if (value.chars().anyMatch(Character::isWhitespace)
                || !value.matches("[A-Za-z0-9.-]+")) {
            throw new DomainException("服务器域名或 IP 格式不正确");
        }
    }

    private static void validateSshUsername(String value) {
        if (value.length() > MAX_SSH_USERNAME_LENGTH
                || !value.matches("[A-Za-z_][A-Za-z0-9_-]*")) {
            throw new DomainException("SSH 用户名格式不正确");
        }
    }

    private static void validateInstallDirectory(String value) {
        if (value.length() > MAX_INSTALL_DIRECTORY_LENGTH) {
            throw new DomainException("中继安装目录不能超过 " + MAX_INSTALL_DIRECTORY_LENGTH + " 个字符");
        }
        if (!value.startsWith("/")
                || value.equals("/")
                || !value.matches("/[A-Za-z0-9._/-]+")
                || value.contains("//")
                || value.contains("/./")
                || value.contains("/../")
                || value.endsWith("/.")
                || value.endsWith("/..")
                || value.endsWith("/")) {
            throw new DomainException("中继安装目录必须是安全的绝对路径");
        }
    }

    private static String normalizeServerUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) return "";
        if (normalized.startsWith("//")) return "https:" + normalized;
        if (!normalized.matches("^[A-Za-z][A-Za-z0-9+.-]*://.*$")) {
            return "https://" + normalized;
        }
        return normalized;
    }

    private static boolean hasUnsafePathSegment(String path) {
        for (String segment : path.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) return true;
        }
        return false;
    }
}
