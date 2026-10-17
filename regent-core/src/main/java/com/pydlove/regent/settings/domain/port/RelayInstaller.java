package com.pydlove.regent.settings.domain.port;

/**
 * 把公网中继安装到远端服务器。
 *
 * <p>密码只存在于一次调用参数中，不进入设置仓储。实现负责把它交给 SSH 认证，
 * 并在失败时返回可直接展示给用户的结果。
 */
public interface RelayInstaller {

    InstallResult install(InstallRequest request);

    record InstallRequest(
            String serverIp,
            String sshUsername,
            String password,
            String installDirectory
    ) {
    }

    record InstallResult(
            boolean success,
            String installDirectory,
            String message
    ) {
        public InstallResult {
            installDirectory = installDirectory == null ? "" : installDirectory;
            message = message == null ? "" : message;
        }
    }
}
