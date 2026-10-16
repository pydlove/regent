package com.pydlove.regent.relay;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RelayRequestRegistryTest {
    @Test
    void 没有连接器时拒绝公网请求() {
        RelayRequestRegistry registry = new RelayRequestRegistry(2, Duration.ofSeconds(5), Duration.ofSeconds(3));

        assertThrows(RelayRequestRegistry.RelayOfflineException.class,
            () -> registry.enqueue("GET", "/api/tasks", Map.of(), new byte[0]));
    }

    @Test
    void 连接器取出请求后可以回填响应() throws Exception {
        RelayRequestRegistry registry = new RelayRequestRegistry(2, Duration.ofSeconds(5), Duration.ofSeconds(3));
        registry.poll(Duration.ofMillis(1));
        RelayRequest original = registry.enqueue(
            "POST",
            "/api/tasks?x=1",
            Map.of("content-type", "application/json"),
            "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        RelayRequest delivered = registry.poll(Duration.ofMillis(100));

        assertEquals(original.id(), delivered.id());
        assertEquals("/api/tasks?x=1", delivered.pathAndQuery());
        registry.complete(delivered.id(), new RelayResponse(201, Map.of(), "ok".getBytes()));

        RelayResponse response = registry.await(original, Duration.ofMillis(100));
        assertEquals(201, response.status());
        assertArrayEquals("ok".getBytes(), response.body());
    }

    @Test
    void 超时后移除等待中的请求() throws Exception {
        RelayRequestRegistry registry = new RelayRequestRegistry(2, Duration.ofSeconds(5), Duration.ofSeconds(3));
        registry.poll(Duration.ofMillis(1));
        RelayRequest request = registry.enqueue("GET", "/api/tasks", Map.of(), new byte[0]);

        assertThrows(TimeoutException.class, () -> registry.await(request, Duration.ofMillis(1)));
        assertEquals(false, registry.complete(request.id(), new RelayResponse(200, Map.of(), new byte[0])));
    }

    @Test
    void 已超时的请求不会再投递给连接器() throws Exception {
        RelayRequestRegistry registry = new RelayRequestRegistry(2, Duration.ofSeconds(5), Duration.ofMillis(10));
        registry.poll(Duration.ofMillis(1));
        registry.enqueue("GET", "/api/tasks", Map.of(), new byte[0]);

        Thread.sleep(20);

        assertNull(registry.poll(Duration.ofMillis(50)));
    }
}
