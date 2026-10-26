package com.pydlove.regent.organization.interfaces.dto;

import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.PasswordHash;
import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.identity.domain.model.UserAvatar;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.model.Username;
import com.pydlove.regent.organization.application.query.OrganizationMemberView;
import com.pydlove.regent.organization.domain.model.OrganizationRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrganizationMemberResponseTest {

    @Test
    @DisplayName("组织成员响应返回带缓存版本的头像地址")
    void exposesVersionedAvatarUrl() {
        User user = User.restore(
                UserId.of(42),
                Email.of("member@example.com"),
                Username.of("member"),
                com.pydlove.regent.identity.domain.model.DisplayName.of("小田"),
                new PasswordHash("hash"),
                new UserAvatar("avatar.png", "image/png", "/tmp/avatar.png",
                        Instant.ofEpochMilli(1234)),
                Instant.EPOCH);

        OrganizationMemberResponse response = OrganizationMemberResponse.from(
                new OrganizationMemberView(user, List.of(OrganizationRole.ORG_MEMBER), List.of()));

        assertEquals("/api/profile/avatar/42?v=1234", response.avatarUrl());
    }

    @Test
    @DisplayName("未上传头像时组织成员响应不伪造地址")
    void leavesAvatarUrlEmptyWithoutAvatar() {
        User user = User.restore(
                UserId.of(42),
                Email.of("member@example.com"),
                Username.of("member"),
                com.pydlove.regent.identity.domain.model.DisplayName.of("小田"),
                new PasswordHash("hash"),
                null,
                Instant.EPOCH);

        OrganizationMemberResponse response = OrganizationMemberResponse.from(
                new OrganizationMemberView(user, List.of(OrganizationRole.ORG_MEMBER), List.of()));

        assertNull(response.avatarUrl());
    }
}
