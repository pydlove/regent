package com.pydlove.regent.relay;

import com.sun.net.httpserver.Headers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        Map<String, String> filtered = RelayHeaders.filterRequest(headers);

        assertEquals("application/json", filtered.get("accept"));
        assertEquals("session=secret", filtered.get("cookie"));
        assertEquals("Bearer secret", filtered.get("authorization"));
    }

    @Test
    void 响应头允许列表包含附件与缓存字段() {
        Map<String, String> filtered = RelayHeaders.filterResponse(Map.of(
            "Content-Type", List.of("image/png"),
            "Content-Disposition", List.of("inline"),
            "Location", List.of("/downloads/Regent.zip"),
            "Set-Cookie", List.of("secret=1"),
            "Transfer-Encoding", List.of("chunked")
        ));

        assertEquals("image/png", filtered.get("content-type"));
        assertEquals("inline", filtered.get("content-disposition"));
        assertEquals("/downloads/Regent.zip", filtered.get("location"));
        assertEquals("secret=1", filtered.get("set-cookie"));
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
}
