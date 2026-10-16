package com.pydlove.regent.shared.domain;

/**
 * 「这个东西不存在」——与「业务规则被违反」分开，是为了让接口层能把它映射成 404 而不是 400。
 *
 * <p>两者都是 {@link DomainException}，所以领域层可以统一按业务失败处理；
 * 区别只在对外呈现，接口层按类型挑选 HTTP 状态码。
 */
public class EntityNotFoundException extends DomainException {

    public EntityNotFoundException(String message) {
        super(message);
    }
}
