package com.pydlove.regent.identity.application.query;

import com.pydlove.regent.identity.domain.model.User;

import java.util.Objects;

/** 当前用户的公开资料。头像版本只用于让前端在替换头像后刷新图片。 */
public record AuthenticatedUser(
        long id,
        String username,
        String displayName,
        String email,
        Long avatarVersion) {

    public static AuthenticatedUser from(User user) {
        Objects.requireNonNull(user, "用户");
        return new AuthenticatedUser(
                user.getId().value(),
                user.getUsername().value(),
                user.getDisplayName().value(),
                user.getEmail() == null ? null : user.getEmail().value(),
                user.getAvatar() == null ? null : user.getAvatar().updatedAt().toEpochMilli());
    }
}
