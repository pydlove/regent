package com.pydlove.regent.project.interfaces.dto;

import java.util.List;

public record ProjectMemberResponse(
        long userId,
        String username,
        String displayName,
        String email,
        String avatarUrl,
        List<String> roles) {
}
