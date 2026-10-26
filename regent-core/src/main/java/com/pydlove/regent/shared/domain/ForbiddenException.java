package com.pydlove.regent.shared.domain;

/** 已登录但当前账号没有执行该操作的权限。 */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super(message);
    }
}
