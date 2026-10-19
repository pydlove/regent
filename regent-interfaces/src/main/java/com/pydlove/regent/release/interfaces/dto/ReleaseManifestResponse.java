package com.pydlove.regent.release.interfaces.dto;

import java.util.Map;

/** 当前发布清单。没有 latest.json 时 version 字段为空，客户端继续使用内置版本。 */
public record ReleaseManifestResponse(
        String version,
        String releaseCode,
        String releasedAt,
        String minShellVersion,
        String notes,
        Map<String, ReleaseAssetResponse> downloads) {
}
