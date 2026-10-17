package com.pydlove.regent.identity.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface AuthSessionMapper {

    int insert(AuthSessionData data);

    AuthPrincipalRow findActivePrincipal(@Param("tokenHash") String tokenHash,
                                         @Param("now") LocalDateTime now);

    int updateExpiresAt(@Param("id") Long id,
                        @Param("expiresAt") LocalDateTime expiresAt);

    int deleteByTokenHash(@Param("tokenHash") String tokenHash);
}
