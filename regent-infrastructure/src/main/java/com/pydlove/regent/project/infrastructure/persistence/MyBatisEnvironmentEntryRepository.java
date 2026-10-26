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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
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
                .map(row -> toDomains(List.of(row)).get(0));
    }

    @Override
    public Optional<EnvironmentEntry> findById(EnvironmentId id, UserId userId) {
        return Optional.ofNullable(mapper.selectByIdOwned(id.value(), userId.value()))
                .map(row -> toDomains(List.of(row)).get(0));
    }

    @Override
    public List<EnvironmentEntry> findAll() {
        return toDomains(mapper.selectAll());
    }

    @Override
    public List<EnvironmentEntry> findAll(UserId userId) {
        return toDomains(mapper.selectAllOwned(userId.value()));
    }

    @Override
    public List<EnvironmentEntry> findApplicableTo(ProjectId projectId) {
        return toDomains(mapper.selectApplicableTo(projectId.value()));
    }

    @Override
    public List<EnvironmentEntry> findApplicableTo(UserId userId, ProjectId projectId) {
        return toDomains(mapper.selectApplicableToOwned(userId.value(), projectId.value()));
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
            } else if (mapper.update(data) != 1) {
                throw new DomainException("环境信息更新失败或已被删除：" + entry.getId());
            }
            replaceProjectLinks(entry);
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

    private List<EnvironmentEntry> toDomains(List<EnvironmentRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> entryIds = rows.stream().map(EnvironmentRow::getId).toList();
        Map<Long, List<Long>> projectIdsByEntry = new LinkedHashMap<>();
        for (EnvironmentProjectRow link : mapper.selectProjectLinks(entryIds)) {
            projectIdsByEntry
                    .computeIfAbsent(link.getEnvironmentId(), ignored -> new ArrayList<>())
                    .add(link.getProjectId());
        }
        return rows.stream()
                .map(row -> EnvironmentDataMapper.toEntry(
                        row,
                        orderedProjectIds(
                                row.getProjectId(),
                                projectIdsByEntry.getOrDefault(
                                        row.getId(),
                                        row.getProjectId() == null
                                                ? List.of()
                                                : List.of(row.getProjectId())))))
                .toList();
    }

    private static List<Long> orderedProjectIds(Long primaryProjectId,
                                                List<Long> linkedProjectIds) {
        if (primaryProjectId == null) {
            return linkedProjectIds;
        }
        List<Long> ordered = new ArrayList<>();
        ordered.add(primaryProjectId);
        linkedProjectIds.stream()
                .filter(projectId -> !projectId.equals(primaryProjectId))
                .forEach(ordered::add);
        return ordered;
    }

    private void replaceProjectLinks(EnvironmentEntry entry) {
        long environmentId = entry.getId().value();
        mapper.deleteProjectLinks(environmentId);
        List<Long> projectIds = entry.getProjectIds().stream().map(ProjectId::value).toList();
        if (!projectIds.isEmpty()) {
            mapper.insertProjectLinks(environmentId, projectIds);
        }
    }
}
