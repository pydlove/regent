package com.pydlove.regent.interfaces.rest;

import com.pydlove.regent.interfaces.dto.ApiError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 未匹配接口必须返回 404，不能掉进兜底分支伪装成服务端故障。 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("未匹配接口返回 404 而不是 500")
    void mapsMissingEndpointToNotFound() {
        ResponseEntity<ApiError> response = handler.endpointNotFound(
                new NoResourceFoundException(HttpMethod.GET, "api/settings/relay")
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("NOT_FOUND", response.getBody().code());
        assertEquals("请求的接口不存在", response.getBody().message());
    }
}
