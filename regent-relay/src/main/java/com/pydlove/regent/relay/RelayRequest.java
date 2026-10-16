package com.pydlove.regent.relay;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

record RelayRequest(
    String id,
    String method,
    String pathAndQuery,
    Map<String, String> headers,
    byte[] body,
    Instant createdAt,
    Instant expiresAt,
    CompletableFuture<RelayResponse> response
) {
    RelayRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(pathAndQuery, "pathAndQuery");
        headers = Map.copyOf(headers);
        body = body == null ? new byte[0] : body.clone();
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(response, "response");
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
