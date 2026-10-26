package com.pydlove.regent.organization.domain.port;

import com.pydlove.regent.identity.domain.model.Email;

import java.time.Duration;

/** 发送组织邀请邮件的外部端口。 */
public interface OrganizationInvitationEmailSender {

    void sendInvitation(Email recipient,
                        String organizationName,
                        String invitationUrl,
                        Duration ttl);
}
