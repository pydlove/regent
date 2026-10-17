package com.pydlove.regent.relay;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayIntegrationTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void 公网请求可以穿过中继到本地并原样返回() throws Exception {
        AtomicReference<String> localRequest = new AtomicReference<>();
        HttpServer local;
        try {
            local = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (java.net.SocketException exception) {
            Assumptions.abort("当前沙箱不允许监听本地端口，跳过中继端到端测试");
            return;
        }
        local.createContext("/api/echo", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            localRequest.set(exchange.getRequestMethod() + " "
                + exchange.getRequestURI()
                + " " + exchange.getRequestHeaders().getFirst("Content-Type")
                + " " + exchange.getRequestHeaders().getFirst("Cookie")
                + " " + body);
            byte[] response = "本地响应".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().add("Set-Cookie", "regent_session=from-local; Path=/; HttpOnly");
            exchange.getResponseHeaders().add("Set-Cookie", "theme=light; Path=/");
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        local.start();

        RelayServerConfig serverConfig = new RelayServerConfig(
            "127.0.0.1", 0, SECRET,
            Duration.ofSeconds(1), Duration.ofSeconds(4), Duration.ofSeconds(3),
            16, 1024 * 1024
        );
        try (RelayHttpServer relay = new RelayHttpServer(serverConfig);
             RelayConnector connector = new RelayConnector(new RelayConnectorConfig(
                 URI.create("http://127.0.0.1:" + relay.port()),
                 URI.create("http://127.0.0.1:" + local.getAddress().getPort()),
                 SECRET, 2, Duration.ofSeconds(1), Duration.ofSeconds(4)
             ))) {
            relay.start();
            connector.start();
            waitUntil(relay::connectorOnline);

            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + relay.port() + "/api/echo?source=phone"))
                .header(RelayHeaders.REQUEST_MARKER_NAME, "1")
                .header("Content-Type", "text/plain; charset=UTF-8")
                .header("Cookie", "regent_session=from-browser; theme=dark")
                .POST(HttpRequest.BodyPublishers.ofString("手机请求"))
                .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            assertEquals(201, response.statusCode());
            assertEquals("本地响应", response.body());
            assertEquals("no-store", response.headers().firstValue("cache-control").orElseThrow());
            assertEquals(java.util.List.of(
                "regent_session=from-local; Path=/; HttpOnly",
                "theme=light; Path=/"
            ), response.headers().allValues("set-cookie"));
            assertTrue(localRequest.get().contains("POST /api/echo?source=phone"));
            assertTrue(localRequest.get().contains("regent_session=from-browser; theme=dark"));
            assertTrue(localRequest.get().contains("手机请求"));
        } finally {
            local.stop(0);
        }
    }

    private static void waitUntil(Check check) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (check.ok()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("等待条件超时");
    }

    @FunctionalInterface
    private interface Check {
        boolean ok();
    }
}
