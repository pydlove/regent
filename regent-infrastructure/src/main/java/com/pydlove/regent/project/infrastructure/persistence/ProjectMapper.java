package com.pydlove.regent.project.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 项目写侧映射接口。SQL 在 {@code mapper/ProjectMapper.xml}。 */
public interface ProjectMapper {

    /** 标识占用检查。**故意不带 deleted = 0** —— 唯一键管的是整张表，删除过的标识也算被占用。 */
    int countByCode(@Param("code") String code);

    int countByOrganizationAndCode(@Param("organizationId") long organizationId,
                                   @Param("code") String code);

    /** 新增。主键由数据库生成，{@code useGeneratedKeys} 回填到 {@link ProjectData#getId()}。 */
    int insert(ProjectData data);

    ProjectData selectById(@Param("id") long id);

    ProjectData selectByIdOwned(@Param("id") long id, @Param("ownerId") long ownerId);

    List<ProjectData> selectBySummaryStatus(@Param("summaryStatus") String summaryStatus);

    int updateSummary(ProjectData data);

    int updateDetails(ProjectData data);
}
