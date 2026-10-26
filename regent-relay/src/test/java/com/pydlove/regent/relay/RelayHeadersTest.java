package com.pydlove.regent.relay;

import com.sun.net.httpserver.Headers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayHeadersTest {
    @Test
    void 转发头名称和值可以无损往返() {
        Headers target = new Headers();
        RelayHeaders.writeForwardedHeaders(target, Map.of("content-type", "text/plain; charset=UTF-8"));

        Map<String, String> decoded = RelayHeaders.readForwardedHeaders(target);

        assertEquals("text/plain; charset=UTF-8", decoded.get("content-type"));
    }

    @Test
    void 只转发白名单请求头() {
        Headers headers = new Headers();
        headers.set("Accept", "application/json");
        headers.set("Authorization", "Bearer secret");
        headers.set("Cookie", "session=secret");
        headers.set("Origin", "capacitor://localhost");
        headers.set("Access-Control-Request-Method", "GET");
        headers.set("Access-Control-Request-Headers", "Authorization");

        Map<String, String> filtered = RelayHeaders.filterRequest(headers);

        assertEquals("application/json", filtered.get("accept"));
        assertEquals("session=secret", filtered.get("cookie"));
        assertEquals("Bearer secret", filtered.get("authorization"));
        assertEquals("capacitor://localhost", filtered.get("origin"));
        assertEquals("GET", filtered.get("access-control-request-method"));
        assertEquals("Authorization", filtered.get("access-control-request-headers"));
    }

    @Test
    void 响应头允许列表包含附件与缓存字段() {
        Map<String, String> filtered = RelayHeaders.filterResponse(Map.of(
            "Content-Type", List.of("image/png"),
            "Content-Disposition", List.of("inline"),
            "Location", List.of("/downloads/Regent.zip"),
            "Set-Cookie", List.of("secret=1"),
            "Access-Control-Allow-Origin", List.of("capacitor://localhost"),
            "Access-Control-Allow-Headers", List.of("Authorization, X-Regent-Relay-Request"),
            "X-Regent-Import-Total", List.of("12"),
            "X-Regent-Import-Failed", List.of("2"),
            "Transfer-Encoding", List.of("chunked")
        ));

        assertEquals("image/png", filtered.get("content-type"));
        assertEquals("inline", filtered.get("content-disposition"));
        assertEquals("/downloads/Regent.zip", filtered.get("location"));
        assertEquals("secret=1", filtered.get("set-cookie"));
        assertEquals("capacitor://localhost", filtered.get("access-control-allow-origin"));
        assertEquals("Authorization, X-Regent-Relay-Request",
            filtered.get("access-control-allow-headers"));
        assertEquals("12", filtered.get("x-regent-import-total"));
        assertEquals("2", filtered.get("x-regent-import-failed"));
        assertTrue(!filtered.containsKey("transfer-encoding"));
    }

    @Test
    void 多个响应头值不会被逗号错误拆分() {
        Map<String, String> filtered = RelayHeaders.filterResponse(Map.of(
            "Set-Cookie", List.of(
                "regent_session=abc; Path=/; Expires=Sat, 17 Oct 2026 08:00:00 GMT",
                "theme=light; Path=/"
            )
        ));

        assertEquals(List.of(
            "regent_session=abc; Path=/; Expires=Sat, 17 Oct 2026 08:00:00 GMT",
            "theme=light; Path=/"
        ), RelayHeaders.splitValues(filtered.get("set-cookie")));
    }

    @Test
    void 写请求接受中继标记或同源Origin但拒绝跨站来源() {
        Headers marked = new Headers();
        marked.set("X-Regent-Relay-Request", "1");
        assertTrue(RelayHeaders.acceptsWrite("POST", marked));

        Headers sameOrigin = new Headers();
        sameOrigin.set("Origin", "https://ichuang.top");
        sameOrigin.set("Host", "ichuang.top");
        assertTrue(RelayHeaders.acceptsWrite("POST", sameOrigin));
        assertTrue(RelayHeaders.isSameOrigin(sameOrigin));

        Headers crossOrigin = new Headers();
        crossOrigin.set("Origin", "https://attacker.example");
        crossOrigin.set("Host", "ichuang.top");
        assertFalse(RelayHeaders.acceptsWrite("POST", crossOrigin));
        assertFalse(RelayHeaders.isSameOrigin(crossOrigin));

        assertTrue(RelayHeaders.acceptsWrite("GET", new Headers()));
    }
}
