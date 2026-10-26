package com.pydlove.regent.identity.application.command;

import com.pydlove.regent.identity.application.query.AuthenticatedUser;

/** 只为其他登录形态校验账号密码，不创建浏览器会话。 */
public interface CredentialAuthenticator {

    AuthenticatedUser authenticateCredentials(LoginCommand command);
}
