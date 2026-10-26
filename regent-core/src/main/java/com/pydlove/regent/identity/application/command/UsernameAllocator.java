package com.pydlove.regent.identity.application.command;

import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.Username;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;

/** 从邮箱生成稳定、可读的登录名；冲突时追加数字后缀。 */
public final class UsernameAllocator {

    private UsernameAllocator() {
    }

    public static Username allocate(UserRepository userRepository, Email email) {
        String base = email.localPart().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isBlank()) {
            base = "user";
        } else if (base.length() < 3) {
            base = "user-" + base;
        }
        base = truncate(base, 24);

        Username first = Username.of(base);
        if (!userRepository.existsByUsername(first)) {
            return first;
        }
        for (int suffix = 2; suffix < 10_000; suffix++) {
            String tail = "-" + suffix;
            Username candidate = Username.of(truncate(base, 32 - tail.length()) + tail);
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new DomainException("无法为该邮箱生成可用账号，请更换邮箱重试");
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
