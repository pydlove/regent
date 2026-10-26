package com.pydlove.regent.project.domain.repository;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.EnvironmentEntry;
import com.pydlove.regent.project.domain.model.EnvironmentId;
import com.pydlove.regent.project.domain.model.EnvironmentScope;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.List;
import java.util.Optional;

/** 环境条目仓储。实现负责领域对象与数据对象之间的转换。 */
public interface EnvironmentEntryRepository {

    Optional<EnvironmentEntry> findById(EnvironmentId id);

    default Optional<EnvironmentEntry> findById(EnvironmentId id, UserId userId) {
        return findById(id);
    }

    List<EnvironmentEntry> findAll();

    default List<EnvironmentEntry> findAll(UserId userId) {
        return findAll();
    }

    /**
     * 查询某个项目可用的全局与项目级条目，全局条目排在前面。
     * 同键冲突的覆盖规则由应用层显式处理。
     */
    List<EnvironmentEntry> findApplicableTo(ProjectId projectId);

    default List<EnvironmentEntry> findApplicableTo(UserId userId, ProjectId projectId) {
        return findApplicableTo(projectId);
    }

    boolean existsByKey(EnvironmentScope scope, ProjectId projectId, String key, EnvironmentId excludingId);

    default boolean existsByKey(EnvironmentScope scope,
                                List<ProjectId> projectIds,
                                String key,
                                EnvironmentId excludingId) {
        return existsByKey(scope, projectIds == null || projectIds.isEmpty() ? null : projectIds.get(0),
                key, excludingId);
    }

    default boolean existsByKey(UserId userId, EnvironmentScope scope, ProjectId projectId,
                                String key, EnvironmentId excludingId) {
        return existsByKey(scope, projectId, key, excludingId);
    }

    default boolean existsByKey(UserId userId,
                                EnvironmentScope scope,
                                List<ProjectId> projectIds,
                                String key,
                                EnvironmentId excludingId) {
        return existsByKey(userId, scope,
                projectIds == null || projectIds.isEmpty() ? null : projectIds.get(0),
                key, excludingId);
    }

    void save(EnvironmentEntry entry);

    void delete(EnvironmentId id);
}
