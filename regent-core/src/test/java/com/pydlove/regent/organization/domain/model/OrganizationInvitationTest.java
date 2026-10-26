package com.pydlove.regent.organization.domain.model;

import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrganizationInvitationTest {

    private static final String TOKEN_HASH = "a".repeat(64);

    @Test
    @DisplayName("邀请只能成功接受一次")
    void acceptsOnce() {
        Instant now = Instant.parse("2026-10-23T08:00:00Z");
        OrganizationInvitation invitation = OrganizationInvitation.create(
                OrganizationId.of(1), Email.of("qa@example.com"),
                OrganizationRole.ORG_MEMBER, TOKEN_HASH, now.plusSeconds(3600), UserId.of(2));

        invitation.accept(UserId.of(3), now);

        assertEquals(OrganizationInvitationStatus.ACCEPTED, invitation.getStatus());
        assertEquals(UserId.of(3), invitation.getAcceptedBy());
        assertThrows(DomainException.class,
                () -> invitation.accept(UserId.of(4), now.plusSeconds(10)));
    }

    @Test
    @DisplayName("过期邀请不能接受")
    void rejectsExpiredInvitation() {
        Instant now = Instant.parse("2026-10-23T08:00:00Z");
        OrganizationInvitation invitation = OrganizationInvitation.create(
                OrganizationId.of(1), Email.of("qa@example.com"),
                OrganizationRole.ORG_MEMBER, TOKEN_HASH, now.minusSeconds(1), UserId.of(2));

        assertThrows(DomainException.class, () -> invitation.accept(UserId.of(3), now));
    }

    @Test
    @DisplayName("组织所有者不能通过邀请授予")
    void rejectsOwnerInvitation() {
        assertThrows(DomainException.class, () -> OrganizationInvitation.create(
                OrganizationId.of(1), Email.of("owner@example.com"),
                OrganizationRole.ORG_OWNER, TOKEN_HASH,
                Instant.now().plusSeconds(3600), UserId.of(2)));
    }
}
