package com.pydlove.regent.relay;

import com.sun.net.httpserver.Headers;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class RelayHeaders {
    static final String REQUEST_MARKER_NAME = "X-Regent-Relay-Request";
    static final String REQUEST_ID = "X-Regent-Relay-Request-Id";
    static final String REQUEST_METHOD = "X-Regent-Relay-Method";
    static final String REQUEST_PATH = "X-Regent-Relay-Path";
    static final String RESPONSE_STATUS = "X-Regent-Relay-Response-Status";
    static final String FORWARDED_HEADER_PREFIX = "X-Regent-Relay-Header-";

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private static final Set<String> REQUEST_HEADERS = Set.of(
        "accept",
        "content-type",
        "if-modified-since",
        "if-none-match",
        "range",
        "x-requested-with"
    );

    private static final Set<String> RESPONSE_HEADERS = Set.of(
        "accept-ranges",
        "cache-control",
        "content-disposition",
        "content-range",
        "content-type",
        "etag",
        "last-modified",
        "vary"
    );

    private RelayHeaders() {
    }

    static String encode(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static String decode(String value) {
        return new String(DECODER.decode(value), StandardCharsets.UTF_8);
    }

    static String headerKey(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return FORWARDED_HEADER_PREFIX + HexFormat.of().formatHex(
            normalized.getBytes(StandardCharsets.UTF_8)
        );
    }

    static Map<String, String> filterRequest(Headers headers) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : REQUEST_HEADERS) {
            List<String> values = headers.get(name);
            if (values != null && !values.isEmpty()) {
                result.put(name, String.join(", ", values));
            }
        }
        return result;
    }

    static Map<String, String> filterResponse(Map<String, List<String>> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (RESPONSE_HEADERS.contains(normalized) && values != null && !values.isEmpty()) {
                result.put(normalized, String.join(", ", values));
            }
        });
        return result;
    }

    static Map<String, String> readForwardedHeaders(Map<String, List<String>> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            if (!name.toLowerCase(Locale.ROOT).startsWith(FORWARDED_HEADER_PREFIX.toLowerCase(Locale.ROOT))) {
                return;
            }
            if (values == null || values.isEmpty()) {
                return;
            }
            String encodedName = name.substring(FORWARDED_HEADER_PREFIX.length());
            result.put(
                new String(HexFormat.of().parseHex(encodedName), StandardCharsets.UTF_8),
                decode(values.get(0))
            );
        });
        return result;
    }

    static void writeForwardedHeaders(Headers target, Map<String, String> headers) {
        headers.forEach((name, value) -> target.set(headerKey(name), encode(value)));
    }

}
