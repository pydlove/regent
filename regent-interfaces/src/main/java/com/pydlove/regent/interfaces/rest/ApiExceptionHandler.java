package com.pydlove.regent.interfaces.rest;

import com.pydlove.regent.interfaces.dto.ApiError;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 异常到 HTTP 的映射。全项目只有这一处做这件事 —— 散在各控制器里的 try/catch
 * 最终一定会对同一个异常给出不同的状态码。
 *
 * <p>状态码的选择：
 * <ul>
 *   <li>{@code EntityNotFoundException} → 404：东西不存在，重试同一请求也没用；</li>
 *   <li>{@code NoResourceFoundException} → 404：请求了不存在的接口，不是服务端故障；</li>
 *   <li>{@link DomainException} → 400：请求本身合法，但违反了业务规则（比如对已完成的任务重试），
 *       属于调用时机不对，调用方该读 message 而不是重试；</li>
 *   <li>其它 → 500：真出了没预料到的问题，日志里留全栈，响应里不带内部细节。</li>
 * </ul>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> notFound(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> endpointNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("NOT_FOUND", "请求的接口不存在"));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> domainViolation(DomainException e) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("DOMAIN_VIOLATION", e.getMessage()));
    }

    /**
     * 上传超过容器限额。不接这一手的话它会掉进兜底分支变成 500「服务端未预期错误」——
     * 而真正的原因（图片太大）只有用户自己能解决。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("UPLOAD_TOO_LARGE", "图片太大，请压缩后再传（单张上限见服务端配置）"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> illegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_ARGUMENT", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("未预期的服务端异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "服务端未预期错误，请查看服务日志"));
    }
}
