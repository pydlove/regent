package com.pydlove.regent.project.infrastructure.persistence;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.EnvironmentEntry;
import com.pydlove.regent.project.domain.model.EnvironmentId;
import com.pydlove.regent.project.domain.model.EnvironmentScope;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.EnvironmentEntryRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 环境信息仓储的 MyBatis 实现。 */
@Repository
public class MyBatisEnvironmentEntryRepository implements EnvironmentEntryRepository {

    private final EnvironmentEntryMapper mapper;

    public MyBatisEnvironmentEntryRepository(EnvironmentEntryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<EnvironmentEntry> findById(EnvironmentId id) {
        return Optional.ofNullable(mapper.selectById(id.value()))
                .map(EnvironmentDataMapper::toEntry);
    }

    @Override
    public Optional<EnvironmentEntry> findById(EnvironmentId id, UserId userId) {
        return Optional.ofNullable(mapper.selectByIdOwned(id.value(), userId.value()))
                .map(EnvironmentDataMapper::toEntry);
    }

    @Override
    public List<EnvironmentEntry> findAll() {
        return mapper.selectAll().stream().map(EnvironmentDataMapper::toEntry).toList();
    }

    @Override
    public List<EnvironmentEntry> findAll(UserId userId) {
        return mapper.selectAllOwned(userId.value()).stream()
                .map(EnvironmentDataMapper::toEntry)
                .toList();
    }

    @Override
    public List<EnvironmentEntry> findApplicableTo(ProjectId projectId) {
        return mapper.selectApplicableTo(projectId.value()).stream()
                .map(EnvironmentDataMapper::toEntry)
                .toList();
    }

    @Override
    public List<EnvironmentEntry> findApplicableTo(UserId userId, ProjectId projectId) {
        return mapper.selectApplicableToOwned(userId.value(), projectId.value()).stream()
                .map(EnvironmentDataMapper::toEntry)
                .toList();
    }

    @Override
    public boolean existsByKey(EnvironmentScope scope,
                               ProjectId projectId,
                               String key,
                               EnvironmentId excludingId) {
        return existsByKey(null, scope, projectId, key, excludingId);
    }

    @Override
    public boolean existsByKey(UserId userId,
                               EnvironmentScope scope,
                               ProjectId projectId,
                               String key,
                               EnvironmentId excludingId) {
        Long projectValue = scope == EnvironmentScope.GLOBAL ? null : projectId.value();
        Long excludingValue = excludingId == null ? null : excludingId.value();
        if (userId == null) {
            return mapper.countByKey(projectValue, key, excludingValue) > 0;
        }
        return mapper.countByKeyOwned(userId.value(), projectValue, key, excludingValue) > 0;
    }

    @Override
    public void save(EnvironmentEntry entry) {
        EnvironmentData data = EnvironmentDataMapper.toData(entry);
        try {
            if (entry.getId() == null) {
                if (mapper.insert(data) != 1) {
                    throw new DomainException("环境信息新增失败：" + entry.getKey());
                }
                entry.assignIdentity(EnvironmentId.of(data.getId()));
                return;
            }
            if (mapper.update(data) != 1) {
                throw new DomainException("环境信息更新失败或已被删除：" + entry.getId());
            }
        } catch (DuplicateKeyException e) {
            throw new DomainException("同一归属已存在环境键：" + entry.getKey(), e);
        }
    }

    @Override
    public void delete(EnvironmentId id) {
        if (mapper.softDelete(id.value()) != 1) {
            throw new DomainException("环境信息删除失败或已被删除：" + id);
        }
    }
}
