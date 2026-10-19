package com.pydlove.regent.project.application.command;

import com.pydlove.regent.project.domain.model.EnvironmentEntry;
import com.pydlove.regent.project.domain.model.EnvironmentId;
import com.pydlove.regent.project.domain.model.EnvironmentScope;
import com.pydlove.regent.project.domain.repository.EnvironmentEntryRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 环境信息的新增、修改与删除。 */
public class EnvironmentEntryService {

    private static final Pattern AUTO_KEY = Pattern.compile("^ENV_(\\d+)$");

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
        requireName(command.description());
        EnvironmentEntry entry = EnvironmentEntry.create(command.scope(), command.projectId(),
                nextKey(), command.value(), command.tags(), command.description(), command.secret());
        requireProjectExists(entry);
        requireKeyAvailable(entry, null);
        repository.save(entry);
        return entry;
    }

    public EnvironmentEntry update(EnvironmentId id, SaveEnvironmentCommand command) {
        requireName(command.description());
        EnvironmentEntry entry = load(id);
        String key = keyForUpdate(command, entry);
        entry.update(command.scope(), command.projectId(), key, command.value(),
                command.tags(), command.description(), command.secret());
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

    /**
     * 环境键由平台生成，用户只填写用于识别的名称。
     *
     * <p>编号在所有归属范围内全局递增：ENV_001、ENV_002……这样同一条信息无论挂全局还是项目，
     * 都不会出现两个相同键指向不同用途，执行器和排障时也只需要认一个编号。
     */
    private String nextKey() {
        int max = repository.findAll().stream()
                .map(EnvironmentEntry::getKey)
                .map(AUTO_KEY::matcher)
                .filter(Matcher::matches)
                .mapToInt(matcher -> Integer.parseInt(matcher.group(1)))
                .max()
                .orElse(0);
        return String.format("ENV_%03d", max + 1);
    }

    private String keyForUpdate(SaveEnvironmentCommand command, EnvironmentEntry entry) {
        String current = entry.getKey();
        if (!repository.existsByKey(command.scope(), command.projectId(), current, entry.getId())) {
            return current;
        }
        return nextKey();
    }

    private static void requireName(String description) {
        if (description == null || description.isBlank()) {
            throw new DomainException("请填写环境信息名称");
        }
    }
}
