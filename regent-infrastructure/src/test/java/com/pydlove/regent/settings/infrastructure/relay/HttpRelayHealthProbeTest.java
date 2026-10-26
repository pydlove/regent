package com.pydlove.regent.settings.infrastructure.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.pydlove.regent.settings.domain.port.RelayHealthProbe;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 中继健康检查适配器对公开响应格式的解析。 */
class HttpRelayHealthProbeTest {

    @Test
    void 读取服务器与连接器状态() throws Exception {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (java.net.SocketException exception) {
            Assumptions.abort("当前沙箱不允许监听本地端口，跳过中继健康检查测试");
            return;
        }
        server.createContext("/regent/health", exchange -> {
            byte[] body = """
                    {"status":"UP","connectorOnline":true,"queueDepth":4}
                    """.trim().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            HttpRelayHealthProbe probe = new HttpRelayHealthProbe(
                    new ObjectMapper(),
                    Duration.ofSeconds(2)
            );

            RelayHealthProbe.ProbeResult result = probe.probe(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/regent/"
            );

            assertTrue(result.reachable());
            assertTrue(result.connectorOnline());
            assertEquals(4, result.queueDepth());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void 非成功响应按服务器不可达处理() {
        HttpRelayHealthProbe probe = new HttpRelayHealthProbe(
                new ObjectMapper(),
                Duration.ofMillis(200)
        );

        RelayHealthProbe.ProbeResult result = probe.probe("http://127.0.0.1:1");

        assertFalse(result.reachable());
        assertFalse(result.connectorOnline());
        assertEquals("无法连接中继服务器", result.message());
    }
}
