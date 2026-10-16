package com.pydlove.regent.relay;

import com.sun.net.httpserver.Headers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RelayConnector implements AutoCloseable {
    private final RelayConnectorConfig config;
    private final HttpClient client;
    private final ExecutorService workers;
    private final AtomicBoolean running = new AtomicBoolean();

    public RelayConnector(RelayConnectorConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        this.workers = Executors.newFixedThreadPool(config.connections(), runnable -> {
            Thread thread = new Thread(runnable, "regent-relay-connector");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        for (int i = 0; i < config.connections(); i++) {
            workers.submit(this::runWorker);
        }
    }

    @Override
    public void close() {
        running.set(false);
        workers.shutdownNow();
    }

    private void runWorker() {
        Duration backoff = Duration.ofSeconds(1);
        while (running.get()) {
            try {
                pollOnce();
                backoff = Duration.ofSeconds(1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                System.err.println("[relay-connector] 连接失败：" + exception.getMessage());
                try {
                    Thread.sleep(backoff.toMillis());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                long nextMillis = Math.min(backoff.multipliedBy(2).toMillis(), Duration.ofSeconds(15).toMillis());
                backoff = Duration.ofMillis(nextMillis);
            }
        }
    }

    private void pollOnce() throws IOException, InterruptedException {
        URI pollUri = serverUri("/api/relay/connector/poll");
        HttpRequest poll = HttpRequest.newBuilder(pollUri)
            .timeout(config.requestTimeout())
            .header("Accept", "application/octet-stream")
            .header("Authorization", "Bearer " + config.sharedSecret())
            .GET()
            .build();

        HttpResponse<byte[]> pollResponse = client.send(poll, HttpResponse.BodyHandlers.ofByteArray());
        if (pollResponse.statusCode() == 204) {
            return;
        }
        if (pollResponse.statusCode() == 401) {
            throw new IOException("中继服务器拒绝了连接器凭据");
        }
        if (pollResponse.statusCode() != 200) {
            throw new IOException("中继服务器返回 HTTP " + pollResponse.statusCode());
        }

        Headers headers = new Headers();
        pollResponse.headers().map().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.set(name, values.get(0));
            }
        });
        String requestId = required(headers, RelayHeaders.REQUEST_ID);
        String method = required(headers, RelayHeaders.REQUEST_METHOD);
        String pathAndQuery = RelayHeaders.decode(required(headers, RelayHeaders.REQUEST_PATH));
        Map<String, String> requestHeaders = RelayHeaders.readForwardedHeaders(pollResponse.headers().map());
        RelayResponse response = executeLocal(method, pathAndQuery, requestHeaders, pollResponse.body());
        sendResponse(requestId, response);
    }

    private RelayResponse executeLocal(
        String method,
        String pathAndQuery,
        Map<String, String> headers,
        byte[] body
    ) throws IOException, InterruptedException {
        URI uri;
        try {
            uri = config.localBase().resolve(pathAndQuery);
        } catch (IllegalArgumentException exception) {
            return errorResponse(400, "本地请求路径不合法");
        }
        if (!sameOrigin(config.localBase(), uri) || uri.getRawPath() == null || !uri.getRawPath().startsWith("/api")) {
            return errorResponse(400, "中继只允许访问本地 /api");
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60));
        headers.forEach(builder::header);
        if (body.length == 0 || "GET".equals(method) || "HEAD".equals(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        }

        HttpResponse<byte[]> localResponse;
        try {
            localResponse = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException exception) {
            return errorResponse(502, "本地 Regent 请求失败：" + exception.getMessage());
        }
        return new RelayResponse(
            localResponse.statusCode(),
            RelayHeaders.filterResponse(localResponse.headers().map()),
            localResponse.body()
        );
    }

    private void sendResponse(String requestId, RelayResponse response) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(serverUri("/api/relay/connector/response"))
            .timeout(config.requestTimeout())
            .header("Authorization", "Bearer " + config.sharedSecret())
            .header(RelayHeaders.REQUEST_ID, requestId)
            .header(RelayHeaders.RESPONSE_STATUS, Integer.toString(response.status()))
            .POST(HttpRequest.BodyPublishers.ofByteArray(response.body()));
        response.headers().forEach((name, value) -> builder.header(RelayHeaders.headerKey(name), RelayHeaders.encode(value)));

        HttpResponse<Void> sent = client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        if (sent.statusCode() != 204) {
            throw new IOException("中继服务器拒绝响应，HTTP " + sent.statusCode());
        }
    }

    private RelayResponse errorResponse(int status, String message) {
        String body = """
            {"code":"LOCAL_REGENT_ERROR","message":"%s"}
            """.formatted(jsonEscape(message)).trim();
        return new RelayResponse(status, Map.of("content-type", "application/json; charset=UTF-8"),
            body.getBytes(StandardCharsets.UTF_8));
    }

    private static String jsonEscape(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
    }

    private URI serverUri(String path) {
        return config.serverBase().resolve(path);
    }

    private static String required(Headers headers, String name) throws IOException {
        String value = headers.getFirst(name);
        if (value == null || value.isBlank()) {
            throw new IOException("中继响应缺少 " + name);
        }
        return value.trim();
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
            && left.getHost().equalsIgnoreCase(right.getHost())
            && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
