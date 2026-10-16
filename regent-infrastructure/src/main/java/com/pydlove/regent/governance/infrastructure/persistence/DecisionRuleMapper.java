package com.pydlove.regent.governance.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 分级规则查询的 MyBatis 映射接口。SQL 在 {@code mapper/DecisionRuleMapper.xml}。 */
public interface DecisionRuleMapper {

    /** projectId 为 null 时只取全局规则；仲裁器仍会自行再筛一次，见领域接口的注释。 */
    List<DecisionRuleRow> selectEnabledFor(@Param("projectId") Long projectId);
}
