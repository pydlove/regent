package com.pydlove.regent.release.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.release.interfaces.dto.ReleaseAssetResponse;
import com.pydlove.regent.release.interfaces.dto.ReleaseManifestResponse;
import com.pydlove.regent.release.interfaces.dto.ReleaseRuntimeResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 无 CDN 的发布分发入口。
 *
 * <p>运维把安装包和 latest.json 放到 {@code ~/.regent/downloads}，应用通过本机后端
 * 提供版本检查和下载。文件不会进入仓库，安装包路径只允许落在该目录内。
 */
@RestController
public class ReleaseController {

    private static final String MANIFEST_FILE = "latest.json";
    private static final String[] PLATFORMS = {"macos", "windows", "android"};

    private final ObjectMapper objectMapper;
    private final Path downloadsDirectory;

    public ReleaseController(ObjectMapper objectMapper,
                             @Value("${regent.downloads.dir}") String downloadsDirectory) {
        this.objectMapper = objectMapper;
        this.downloadsDirectory = Path.of(downloadsDirectory).toAbsolutePath().normalize();
    }

    @GetMapping("/api/release/latest")
    public ReleaseManifestResponse latest(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        JsonNode manifest = readManifest();
        if (manifest == null) {
            return new ReleaseManifestResponse(
                    null, null, null, false, null, null, null, null, Map.of());
        }
        Map<String, ReleaseAssetResponse> downloads = new LinkedHashMap<>();
        for (String platform : PLATFORMS) {
            JsonNode asset = manifest.path("downloads").path(platform);
            String filename = safeFilename(text(asset, "file"));
            if (filename == null || !Files.isRegularFile(downloadFile(filename))) {
                continue;
            }
            downloads.put(platform, new ReleaseAssetResponse(
                    filename,
                    "/downloads/" + UriUtils.encodePathSegment(filename, StandardCharsets.UTF_8),
                    asset.path("sizeBytes").asLong(0),
                    text(asset, "sha256")));
        }
        ReleaseRuntimeResponse runtime = runtime(manifest);
        return new ReleaseManifestResponse(
                text(manifest, "version"),
                text(manifest, "releaseCode"),
                text(manifest, "clientReleaseCode"),
                manifest.path("webOnly").asBoolean(false),
                text(manifest, "releasedAt"),
                text(manifest, "minShellVersion"),
                text(manifest, "notes"),
                runtime,
                downloads);
    }

    private ReleaseRuntimeResponse runtime(JsonNode manifest) {
        JsonNode asset = manifest.path("runtime");
        String filename = safeFilename(text(asset, "file"));
        if (filename == null || !Files.isRegularFile(downloadFile(filename))) {
            return null;
        }
        return new ReleaseRuntimeResponse(
                text(manifest, "version"),
                text(manifest, "releaseCode"),
                text(asset, "minShellRelease"),
                filename,
                "/downloads/" + UriUtils.encodePathSegment(filename, StandardCharsets.UTF_8),
                asset.path("sizeBytes").asLong(0),
                text(asset, "sha256"),
                text(asset, "signature"));
    }

    @GetMapping("/api/release/download/{platform}")
    public ResponseEntity<Void> download(@PathVariable String platform) {
        if (!isKnownPlatform(platform)) {
            return ResponseEntity.notFound().build();
        }
        JsonNode manifest = readManifest();
        if (manifest == null) {
            return ResponseEntity.notFound().build();
        }
        String filename = safeFilename(
                text(manifest.path("downloads").path(platform), "file"));
        if (filename == null) {
            return ResponseEntity.notFound().build();
        }
        Path file = downloadFile(filename);
        if (!Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/downloads/" + UriUtils.encodePathSegment(
                        filename, StandardCharsets.UTF_8)))
                .build();
    }

    /**
     * 公网下载的同一路径。Nginx 在生产环境直接从这里读静态文件，不再经过中继；
     * 本地开发由 Vite 把 /downloads 代理到本机后端。
     */
    @GetMapping("/downloads/{filename}")
    public ResponseEntity<InputStreamResource> downloadByName(@PathVariable String filename) throws IOException {
        String safeFilename = safeFilename(filename);
        if (safeFilename == null) {
            return ResponseEntity.notFound().build();
        }
        Path file = downloadFile(safeFilename);
        if (!Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        return serve(file, safeFilename);
    }

    private static ResponseEntity<InputStreamResource> serve(Path file, String filename) throws IOException {
        MediaType mediaType = MediaTypeFactory.getMediaType(filename)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .contentType(mediaType)
                .contentLength(Files.size(file))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(new InputStreamResource(Files.newInputStream(file)));
    }

    private JsonNode readManifest() {
        Path path = downloadsDirectory.resolve(MANIFEST_FILE).normalize();
        if (!path.startsWith(downloadsDirectory) || !Files.isRegularFile(path)) {
            return null;
        }
        try {
            return objectMapper.readTree(path.toFile());
        } catch (IOException e) {
            return null;
        }
    }

    private Path downloadFile(String filename) {
        return downloadsDirectory.resolve(filename).normalize();
    }

    private static String safeFilename(String filename) {
        if (filename == null || filename.isBlank()
                || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return null;
        }
        return filename;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static boolean isKnownPlatform(String platform) {
        for (String known : PLATFORMS) {
            if (known.equals(platform)) {
                return true;
            }
        }
        return false;
    }
}
