package com.pydlove.regent.gitcommit.infrastructure.persistence;

import com.pydlove.regent.gitcommit.application.query.GitCommitFilter;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** Git 提交只读查询的 MyBatis 映射。 */
public interface GitCommitQueryMapper {

    List<GitCommitQueryRow> selectByTask(@Param("taskId") long taskId,
                                         @Param("ownerId") long ownerId);

    List<GitCommitQueryRow> selectPage(@Param("ownerId") long ownerId,
                                       @Param("filter") GitCommitFilter filter,
                                       @Param("offset") int offset);

    long countPage(@Param("ownerId") long ownerId,
                   @Param("filter") GitCommitFilter filter);

    GitCommitQueryRow selectDetail(@Param("id") long id,
                                   @Param("ownerId") long ownerId);
}
