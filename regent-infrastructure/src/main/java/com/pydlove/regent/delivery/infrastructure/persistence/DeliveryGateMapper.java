package com.pydlove.regent.delivery.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface DeliveryGateMapper {

    @Select("""
            SELECT id, scope_type, scope_id, gate_type, decision, role_code, comment, decided_by, decided_at
            FROM delivery_gate
            WHERE scope_type = #{scopeType} AND scope_id = #{scopeId}
            ORDER BY id
            """)
    List<DeliveryGateData> selectByScope(@Param("scopeType") String scopeType,
                                         @Param("scopeId") long scopeId);

    @Insert("""
            INSERT INTO delivery_gate (
                scope_type, scope_id, gate_type, decision, role_code, comment, decided_by, decided_at
            ) VALUES (
                #{scopeType}, #{scopeId}, #{gateType}, #{decision}, #{roleCode}, #{comment},
                #{decidedBy}, #{decidedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DeliveryGateData data);
}
