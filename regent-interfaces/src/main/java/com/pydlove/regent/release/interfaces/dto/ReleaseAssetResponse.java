package com.pydlove.regent.release.interfaces.dto;

/** 下载页使用的安装包信息。url 是后端本机托管的下载地址，不需要 CDN。 */
public record ReleaseAssetResponse(
        String filename,
        String url,
        long sizeBytes,
        String sha256) {
}
