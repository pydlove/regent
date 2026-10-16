package com.pydlove.regent.project.application.command;

import com.pydlove.regent.project.domain.model.EnvironmentEntry;
import com.pydlove.regent.project.domain.model.EnvironmentId;
import com.pydlove.regent.project.domain.model.EnvironmentScope;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.EnvironmentEntryRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentEntryServiceTest {

    private final MemoryRepository repository = new MemoryRepository();
    private final Projects projects = new Projects(List.of(
            new ProjectProfile(ProjectId.of(1L), "one", "项目一", "/tmp/one", DecisionLevel.L1, true),
            new ProjectProfile(ProjectId.of(2L), "two", "项目二", "/tmp/two", DecisionLevel.L1, true)));
    private EnvironmentEntryService service;

    @BeforeEach
    void setUp() {
        service = new EnvironmentEntryService(repository, projects);
    }

    @Test
    @DisplayName("可以创建全局与项目级环境信息")
    void createsScopedEntries() {
        EnvironmentEntry global = service.create(command(EnvironmentScope.GLOBAL, null, "DB_HOST"));
        EnvironmentEntry project = service.create(command(EnvironmentScope.PROJECT, ProjectId.of(1L), "DB_HOST"));

        assertEquals(EnvironmentScope.GLOBAL, global.getScope());
        assertEquals(EnvironmentScope.PROJECT, project.getScope());
        assertEquals(2, service.list().size());
    }

    @Test
    @DisplayName("同一归属内键不能重复，但项目键可以覆盖同名全局键")
    void preventsDuplicateWithinScopeOnly() {
        service.create(command(EnvironmentScope.GLOBAL, null, "DB_HOST"));
        service.create(command(EnvironmentScope.PROJECT, ProjectId.of(1L), "DB_HOST"));

        DomainException error = assertThrows(DomainException.class,
                () -> service.create(command(EnvironmentScope.GLOBAL, null, "DB_HOST")));

        assertTrue(error.getMessage().contains("全局已存在环境键"), error.getMessage());
    }

    @Test
    @DisplayName("项目级环境必须指向真实项目")
    void rejectsUnknownProject() {
        assertThrows(EntityNotFoundException.class,
                () -> service.create(command(EnvironmentScope.PROJECT, ProjectId.of(99L), "DB_HOST")));
        assertTrue(repository.entries.isEmpty());
    }

    @Test
    @DisplayName("修改后可以改变归属，但仍要校验新项目的存在性与键冲突")
    void updatesScopeAndValidatesAgain() {
        EnvironmentEntry first = service.create(command(EnvironmentScope.GLOBAL, null, "DB_HOST"));
        service.create(command(EnvironmentScope.PROJECT, ProjectId.of(1L), "DB_HOST"));

        service.update(first.getId(), new SaveEnvironmentCommand(
                EnvironmentScope.PROJECT, ProjectId.of(2L), "DB_HOST", "db-two",
                List.of("数据库"), "项目二数据库"));

        assertEquals(ProjectId.of(2L), first.getProjectId());
        assertThrows(EntityNotFoundException.class, () -> service.update(first.getId(),
                new SaveEnvironmentCommand(EnvironmentScope.PROJECT, ProjectId.of(99L),
                        "OTHER", "x", List.of(), null)));
    }

    @Test
    @DisplayName("删除后仓储查不到")
    void deletesEntry() {
        EnvironmentEntry entry = service.create(command(EnvironmentScope.GLOBAL, null, "DB_HOST"));

        service.delete(entry.getId());

        assertFalse(repository.findById(entry.getId()).isPresent());
    }

    private static SaveEnvironmentCommand command(EnvironmentScope scope, ProjectId projectId, String key) {
        return new SaveEnvironmentCommand(scope, projectId, key, "value", List.of("测试"), "说明");
    }

    private static final class MemoryRepository implements EnvironmentEntryRepository {

        private final Map<Long, EnvironmentEntry> entries = new LinkedHashMap<>();
        private long sequence;

        @Override
        public Optional<EnvironmentEntry> findById(EnvironmentId id) {
            return Optional.ofNullable(entries.get(id.value()));
        }

        @Override
        public List<EnvironmentEntry> findAll() {
            return List.copyOf(entries.values());
        }

        @Override
        public List<EnvironmentEntry> findApplicableTo(ProjectId projectId) {
            return entries.values().stream()
                    .filter(entry -> entry.getScope() == EnvironmentScope.GLOBAL
                            || entry.getProjectId().equals(projectId))
                    .toList();
        }

        @Override
        public boolean existsByKey(EnvironmentScope scope,
                                   ProjectId projectId,
                                   String key,
                                   EnvironmentId excludingId) {
            return entries.values().stream()
                    .filter(entry -> excludingId == null || !entry.getId().equals(excludingId))
                    .anyMatch(entry -> entry.getScope() == scope
                            && java.util.Objects.equals(entry.getProjectId(), projectId)
                            && entry.getKey().equals(key));
        }

        @Override
        public void save(EnvironmentEntry entry) {
            if (entry.getId() == null) {
                entry.assignIdentity(EnvironmentId.of(++sequence));
            }
            entries.put(entry.getId().value(), entry);
        }

        @Override
        public void delete(EnvironmentId id) {
            entries.remove(id.value());
        }
    }

    private record Projects(List<ProjectProfile> profiles) implements ProjectProfileRepository {

        @Override
        public Optional<ProjectProfile> findById(ProjectId id) {
            return profiles.stream().filter(profile -> profile.id().equals(id)).findFirst();
        }

        @Override
        public Optional<ProjectProfile> findByCode(String code) {
            return profiles.stream().filter(profile -> profile.code().equals(code)).findFirst();
        }

        @Override
        public List<ProjectProfile> findAll() {
            return profiles;
        }
    }
}
