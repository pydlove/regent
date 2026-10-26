package com.pydlove.regent.project.application.command;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.EnvironmentEntry;
import com.pydlove.regent.project.domain.model.EnvironmentId;
import com.pydlove.regent.project.domain.model.EnvironmentScope;
import com.pydlove.regent.project.domain.model.ProjectId;
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

    public List<EnvironmentEntry> list(UserId userId) {
        return repository.findAll(userId);
    }

    public EnvironmentEntry create(SaveEnvironmentCommand command) {
        return create(command, null);
    }

    public EnvironmentEntry create(SaveEnvironmentCommand command, UserId userId) {
        requireName(command.description());
        EnvironmentEntry entry = EnvironmentEntry.create(userId, command.scope(), command.projectIds(),
                nextKey(), command.value(), command.tags(), command.description(), command.secret());
        requireProjectExists(entry, userId);
        requireKeyAvailable(entry, null, userId);
        repository.save(entry);
        return entry;
    }

    public EnvironmentEntry update(EnvironmentId id, SaveEnvironmentCommand command) {
        return update(id, command, null);
    }

    public EnvironmentEntry update(EnvironmentId id, SaveEnvironmentCommand command, UserId userId) {
        requireName(command.description());
        EnvironmentEntry entry = load(id, userId);
        String key = keyForUpdate(command, entry, userId);
        entry.update(command.scope(), command.projectIds(), key, command.value(),
                command.tags(), command.description(), command.secret());
        requireProjectExists(entry, userId);
        requireKeyAvailable(entry, id, userId);
        repository.save(entry);
        return entry;
    }

    public void delete(EnvironmentId id) {
        delete(id, null);
    }

    public void delete(EnvironmentId id, UserId userId) {
        load(id, userId);
        repository.delete(id);
    }

    private EnvironmentEntry load(EnvironmentId id) {
        return load(id, null);
    }

    private EnvironmentEntry load(EnvironmentId id, UserId userId) {
        return (userId == null ? repository.findById(id) : repository.findById(id, userId))
                .orElseThrow(() -> new EntityNotFoundException("环境信息不存在：" + id));
    }

    private void requireProjectExists(EnvironmentEntry entry, UserId userId) {
        if (entry.getScope() == EnvironmentScope.PROJECT) {
            for (ProjectId projectId : entry.getProjectIds()) {
                if ((userId == null
                        ? projectProfileRepository.findById(projectId)
                        : projectProfileRepository.findById(projectId, userId)).isEmpty()) {
                    throw new EntityNotFoundException("项目不存在：" + projectId);
                }
            }
        }
    }

    private void requireKeyAvailable(EnvironmentEntry entry, EnvironmentId excludingId, UserId userId) {
        boolean exists = userId == null
                ? repository.existsByKey(entry.getScope(), entry.getProjectIds(), entry.getKey(), excludingId)
                : repository.existsByKey(userId, entry.getScope(), entry.getProjectIds(),
                        entry.getKey(), excludingId);
        if (exists) {
            String owner = entry.getScope() == EnvironmentScope.GLOBAL
                    ? "全局"
                    : "所选项目";
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
        return nextKey(null);
    }

    private String nextKey(UserId userId) {
        int max = (userId == null ? repository.findAll() : repository.findAll(userId)).stream()
                .map(EnvironmentEntry::getKey)
                .map(AUTO_KEY::matcher)
                .filter(Matcher::matches)
                .mapToInt(matcher -> Integer.parseInt(matcher.group(1)))
                .max()
                .orElse(0);
        return String.format("ENV_%03d", max + 1);
    }

    private String keyForUpdate(SaveEnvironmentCommand command, EnvironmentEntry entry, UserId userId) {
        String current = entry.getKey();
        boolean exists = userId == null
                ? repository.existsByKey(command.scope(), command.projectIds(), current, entry.getId())
                : repository.existsByKey(userId, command.scope(), command.projectIds(),
                        current, entry.getId());
        if (!exists) {
            return current;
        }
        return nextKey(userId);
    }

    private static void requireName(String description) {
        if (description == null || description.isBlank()) {
            throw new DomainException("请填写环境信息名称");
        }
    }
}
