package com.pydlove.regent.identity.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface MobileAuthSessionMapper {

    int insert(MobileAuthSessionData data);

    MobileAuthSessionData findActiveByAccessToken(@Param("tokenHash") String tokenHash,
                                                   @Param("now") LocalDateTime now);

    MobileAuthSessionData findByRefreshTokenForUpdate(@Param("tokenHash") String tokenHash);

    MobileAuthSessionData findByIdForUpdate(@Param("id") long id);

    Long findSessionIdByUsedRefreshToken(@Param("tokenHash") String tokenHash);

    int insertRefreshTokenUse(@Param("sessionId") long sessionId,
                              @Param("tokenHash") String tokenHash,
                              @Param("usedAt") LocalDateTime usedAt);

    int update(MobileAuthSessionData data);

    int revokeActiveByInstallation(@Param("userId") long userId,
                                   @Param("installationId") String installationId,
                                   @Param("now") LocalDateTime now);
}
