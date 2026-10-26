package com.pydlove.regent.organization.domain.port;

public interface OrganizationInvitationTokenCodec {

    String generateRawToken();

    String hash(String rawToken);
}
