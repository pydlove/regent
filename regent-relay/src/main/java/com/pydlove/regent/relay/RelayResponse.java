package com.pydlove.regent.relay;

import java.util.Map;
import java.util.Objects;

record RelayResponse(int status, Map<String, String> headers, byte[] body) {
    RelayResponse {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("HTTP 状态码不合法：" + status);
        }
        headers = Map.copyOf(headers);
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
