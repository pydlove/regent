package com.pydlove.regent.release.interfaces.dto;

/** 可增量安装的桌面 Runtime 包。旧客户端可以忽略并继续使用完整安装包。 */
public record ReleaseRuntimeResponse(
        String version,
        String releaseCode,
        String minShellRelease,
        String filename,
        String url,
        long sizeBytes,
        String sha256,
        String signature) {
}
