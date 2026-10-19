package com.pydlove.regent.relay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelayConnectorTest {

    @Test
    @DisplayName("本地请求异常没有消息时返回异常类型")
    void usesExceptionTypeWhenMessageIsMissing() {
        assertEquals("IOException", RelayConnector.failureMessage(new IOException()));
    }

    @Test
    @DisplayName("本地请求异常保留原始消息")
    void keepsOriginalMessage() {
        assertEquals("connection reset", RelayConnector.failureMessage(
                new IOException("connection reset")));
    }
}
