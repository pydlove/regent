package com.pydlove.regent.governance.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 审批令牌的 MyBatis 映射接口。 */
public interface ApprovalTokenMapper {

    ApprovalTokenRow selectByHash(@Param("tokenHash") String tokenHash);

    int updateConsumed(@Param("id") long id,
                       @Param("usedIp") String usedIp,
                       @Param("usedAt") LocalDateTime usedAt);
}
