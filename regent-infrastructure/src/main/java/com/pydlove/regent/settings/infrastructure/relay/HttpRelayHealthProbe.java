package com.pydlove.regent.settings.infrastructure.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.settings.domain.port.RelayHealthProbe;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 通过中继公开的 {@code /health} 读取服务器与连接器状态。
 *
 * <p>这里使用独立的短超时，且不跟随重定向：设置页的一次状态轮询不应该被失联服务器拖成
 * 长请求，也不应该被重定向带到未配置的主机。
 */
public class HttpRelayHealthProbe implements RelayHealthProbe {

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;

    public HttpRelayHealthProbe(ObjectMapper objectMapper, Duration requestTimeout) {
        this.objectMapper = objectMapper;
        this.requestTimeout = requestTimeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public ProbeResult probe(String serverUrl) {
        long startedAt = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(healthUri(serverUrl))
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8)
            );
            long latency = elapsedMillis(startedAt);
            if (response.statusCode() != 200) {
                return unreachable(latency, "中继服务器返回 HTTP " + response.statusCode());
            }

            JsonNode body = objectMapper.readTree(response.body());
            if (!"UP".equalsIgnoreCase(body.path("status").asText())) {
                return unreachable(latency, "中继服务器健康状态异常");
            }
            boolean connectorOnline = body.path("connectorOnline").asBoolean(false);
            int queueDepth = Math.max(0, body.path("queueDepth").asInt(0));
            return new ProbeResult(
                    true,
                    connectorOnline,
                    queueDepth,
                    latency,
                    connectorOnline ? "中继服务器与连接器均在线" : "中继服务器在线，但连接器离线"
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return unreachable(elapsedMillis(startedAt), "中继健康检查被中断");
        } catch (Exception exception) {
            return unreachable(elapsedMillis(startedAt), "无法连接中继服务器");
        }
    }

    private static URI healthUri(String serverUrl) {
        String base = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
        return URI.create(base).resolve("health");
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private static ProbeResult unreachable(long latency, String message) {
        return new ProbeResult(false, false, 0, latency, message);
    }
}
