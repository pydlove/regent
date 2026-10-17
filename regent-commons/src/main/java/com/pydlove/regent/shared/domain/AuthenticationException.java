package com.pydlove.regent.shared.domain;

/**
 * 身份认证失败。与普通领域异常分开，接口层才能稳定映射成 401，
 * 而不是让「没登录」被当成一次普通的参数错误。
 */
public class AuthenticationException extends DomainException {

    public AuthenticationException(String message) {
        super(message);
    }
}
