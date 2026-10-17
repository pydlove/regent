package com.pydlove.regent.relay;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

public final class RelayHttpServer implements AutoCloseable {
    private static final String CONNECTOR_POLL_PATH = "/api/relay/connector/poll";
    private static final String CONNECTOR_RESPONSE_PATH = "/api/relay/connector/response";

    private final RelayServerConfig config;
    private final RelayRequestRegistry registry;
    private final HttpServer server;
    private final ExecutorService executor;

    public RelayHttpServer(RelayServerConfig config) throws IOException {
        this.config = config;
        this.registry = new RelayRequestRegistry(
            config.queueCapacity(),
            config.connectorOfflineAfter(),
            config.requestTimeout()
        );
        this.server = HttpServer.create(new InetSocketAddress(config.bindAddress(), config.port()), 0);
        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "regent-relay-http");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public boolean connectorOnline() {
        return registry.connectorOnline();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (BodyTooLargeException exception) {
            if (exchange.getResponseCode() == -1) {
                sendJson(exchange, 413, "RELAY_BODY_TOO_LARGE", "请求体超过中继上限");
            }
        } catch (Exception exception) {
            System.err.println("[relay] 处理请求失败：" + exception.getMessage());
            if (exchange.getResponseCode() == -1) {
                sendJson(exchange, 500, "RELAY_INTERNAL_ERROR", "中继服务内部错误");
            }
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange) throws Exception {
        String path = exchange.getRequestURI().getPath();
        if (CONNECTOR_POLL_PATH.equals(path)) {
            handleConnectorPoll(exchange);
            return;
        }
        if (CONNECTOR_RESPONSE_PATH.equals(path)) {
            handleConnectorResponse(exchange);
            return;
        }
        if ("/health".equals(path)) {
            handleHealth(exchange);
            return;
        }
        handlePublicApi(exchange);
    }

    private void handleConnectorPoll(HttpExchange exchange) throws IOException, InterruptedException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "METHOD_NOT_ALLOWED", "只允许 GET");
            return;
        }
        if (!authorized(exchange)) {
            sendJson(exchange, 401, "RELAY_UNAUTHORIZED", "中继凭据无效");
            return;
        }

        RelayRequest request = registry.poll(config.connectorPollWait());
        if (request == null) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        Headers headers = exchange.getResponseHeaders();
        headers.set(RelayHeaders.REQUEST_ID, request.id());
        headers.set(RelayHeaders.REQUEST_METHOD, request.method());
        headers.set(RelayHeaders.REQUEST_PATH, RelayHeaders.encode(request.pathAndQuery()));
        RelayHeaders.writeForwardedHeaders(headers, request.headers());
        byte[] body = request.body();
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
    }

    private void handleConnectorResponse(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "METHOD_NOT_ALLOWED", "只允许 POST");
            return;
        }
        if (!authorized(exchange)) {
            sendJson(exchange, 401, "RELAY_UNAUTHORIZED", "中继凭据无效");
            return;
        }

        String requestId = singleHeader(exchange.getRequestHeaders(), RelayHeaders.REQUEST_ID);
        String statusValue = singleHeader(exchange.getRequestHeaders(), RelayHeaders.RESPONSE_STATUS);
        if (requestId == null || statusValue == null) {
            sendJson(exchange, 400, "RELAY_BAD_RESPONSE", "响应缺少请求 ID 或状态码");
            return;
        }

        int status;
        try {
            status = Integer.parseInt(statusValue);
        } catch (NumberFormatException exception) {
            sendJson(exchange, 400, "RELAY_BAD_RESPONSE", "响应状态码不合法");
            return;
        }

        byte[] body = readBody(exchange, config.maxBodyBytes());
        Map<String, String> headers = RelayHeaders.readForwardedHeaders(exchange.getRequestHeaders());
        try {
            RelayResponse response = new RelayResponse(status, headers, body);
            registry.complete(requestId, response);
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, "RELAY_BAD_RESPONSE", exception.getMessage());
            return;
        }
        exchange.sendResponseHeaders(204, -1);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "METHOD_NOT_ALLOWED", "只允许 GET");
            return;
        }
        String body = """
            {"status":"UP","connectorOnline":%s,"queueDepth":%d}
            """.formatted(registry.connectorOnline(), registry.queueDepth()).trim();
        send(exchange, 200, "application/json; charset=UTF-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private void handlePublicApi(HttpExchange exchange) throws IOException, InterruptedException {
        URI uri = exchange.getRequestURI();
        String rawPath = uri.getRawPath();
        if (rawPath == null || (!rawPath.equals("/api") && !rawPath.startsWith("/api/"))) {
            sendJson(exchange, 404, "NOT_FOUND", "中继只转发 /api");
            return;
        }
        if (uri.isAbsolute() || rawPath.startsWith("//") || rawPath.indexOf('\r') >= 0 || rawPath.indexOf('\n') >= 0) {
            sendJson(exchange, 400, "RELAY_BAD_REQUEST", "请求路径不合法");
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())
            && !"HEAD".equals(exchange.getRequestMethod())
            && !"OPTIONS".equals(exchange.getRequestMethod())
            && !"1".equals(exchange.getRequestHeaders().getFirst(RelayHeaders.REQUEST_MARKER_NAME))) {
            sendJson(exchange, 403, "RELAY_CSRF_REJECTED", "写操作缺少中继请求标记");
            return;
        }

        String pathAndQuery = rawPath;
        if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
            pathAndQuery += "?" + uri.getRawQuery();
        }

        if (!registry.connectorOnline()) {
            sendJson(exchange, 503, "RELAY_OFFLINE", "本地 Regent 未连接，请确认中继连接器已启动");
            return;
        }

        byte[] body = readBody(exchange, config.maxBodyBytes());
        RelayRequest request;
        try {
            request = registry.enqueue(
                exchange.getRequestMethod(),
                pathAndQuery,
                RelayHeaders.filterRequest(exchange.getRequestHeaders()),
                body
            );
        } catch (RelayRequestRegistry.RelayOfflineException exception) {
            sendJson(exchange, 503, "RELAY_OFFLINE", "本地 Regent 未连接，请确认中继连接器已启动");
            return;
        } catch (RelayRequestRegistry.RelayQueueFullException exception) {
            sendJson(exchange, 503, "RELAY_BUSY", "中继请求队列已满，请稍后重试");
            return;
        }

        try {
            RelayResponse response = registry.await(request, config.requestTimeout());
            Headers target = exchange.getResponseHeaders();
            response.headers().forEach((name, values) ->
                RelayHeaders.splitValues(values).forEach(value -> target.add(name, value)));
            byte[] responseBody = response.body();
            if ("HEAD".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(response.status(), responseBody.length == 0 ? -1 : responseBody.length);
            } else {
                int length = response.status() == 204 || response.status() == 304 ? -1 : responseBody.length;
                exchange.sendResponseHeaders(response.status(), length);
                if (responseBody.length > 0 && length >= 0) {
                    exchange.getResponseBody().write(responseBody);
                }
            }
        } catch (TimeoutException exception) {
            sendJson(exchange, 504, "RELAY_TIMEOUT", "本地 Regent 响应超时");
        } catch (ExecutionException exception) {
            sendJson(exchange, 502, "RELAY_BAD_RESPONSE", "本地 Regent 响应失败");
        }
    }

    private boolean authorized(HttpExchange exchange) {
        String supplied = exchange.getRequestHeaders().getFirst("Authorization");
        String expected = "Bearer " + config.sharedSecret();
        if (supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(
            supplied.getBytes(StandardCharsets.UTF_8),
            expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String singleHeader(Headers headers, String name) {
        String value = headers.getFirst(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static byte[] readBody(HttpExchange exchange, int maxBytes) throws IOException {
        try (InputStream input = exchange.getRequestBody();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) {
                    throw new BodyTooLargeException();
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String code, String message)
        throws IOException {
        String body = """
            {"code":"%s","message":"%s"}
            """.formatted(code, message).trim();
        send(exchange, status, "application/json; charset=UTF-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body)
        throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
    }

    private static final class BodyTooLargeException extends RuntimeException {
    }
}
