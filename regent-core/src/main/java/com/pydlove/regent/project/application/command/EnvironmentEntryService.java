package com.pydlove.regent.project.application.command;

import com.pydlove.regent.project.domain.model.EnvironmentEntry;
import com.pydlove.regent.project.domain.model.EnvironmentId;
import com.pydlove.regent.project.domain.model.EnvironmentScope;
import com.pydlove.regent.project.domain.repository.EnvironmentEntryRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.List;

/** 环境信息的新增、修改与删除。 */
public class EnvironmentEntryService {

    private final EnvironmentEntryRepository repository;
    private final ProjectProfileRepository projectProfileRepository;

    public EnvironmentEntryService(EnvironmentEntryRepository repository,
                                   ProjectProfileRepository projectProfileRepository) {
        this.repository = repository;
        this.projectProfileRepository = projectProfileRepository;
    }

    public List<EnvironmentEntry> list() {
        return repository.findAll();
    }

    public EnvironmentEntry create(SaveEnvironmentCommand command) {
        EnvironmentEntry entry = EnvironmentEntry.create(command.scope(), command.projectId(),
                command.key(), command.value(), command.tags(), command.description());
        requireProjectExists(entry);
        requireKeyAvailable(entry, null);
        repository.save(entry);
        return entry;
    }

    public EnvironmentEntry update(EnvironmentId id, SaveEnvironmentCommand command) {
        EnvironmentEntry entry = load(id);
        entry.update(command.scope(), command.projectId(), command.key(), command.value(),
                command.tags(), command.description());
        requireProjectExists(entry);
        requireKeyAvailable(entry, id);
        repository.save(entry);
        return entry;
    }

    public void delete(EnvironmentId id) {
        load(id);
        repository.delete(id);
    }

    private EnvironmentEntry load(EnvironmentId id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("环境信息不存在：" + id));
    }

    private void requireProjectExists(EnvironmentEntry entry) {
        if (entry.getScope() == EnvironmentScope.PROJECT
                && projectProfileRepository.findById(entry.getProjectId()).isEmpty()) {
            throw new EntityNotFoundException("项目不存在：" + entry.getProjectId());
        }
    }

    private void requireKeyAvailable(EnvironmentEntry entry, EnvironmentId excludingId) {
        if (repository.existsByKey(entry.getScope(), entry.getProjectId(), entry.getKey(), excludingId)) {
            String owner = entry.getScope() == EnvironmentScope.GLOBAL
                    ? "全局"
                    : "项目 " + entry.getProjectId();
            throw new DomainException(owner + "已存在环境键：" + entry.getKey());
        }
    }
}
