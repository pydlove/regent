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
    @DisplayName("创建时自动生成全局唯一的 ENV 编号")
    void createsScopedEntries() {
        EnvironmentEntry global = service.create(command(EnvironmentScope.GLOBAL, null));
        EnvironmentEntry project = service.create(command(EnvironmentScope.PROJECT, ProjectId.of(1L)));

        assertEquals(EnvironmentScope.GLOBAL, global.getScope());
        assertEquals(EnvironmentScope.PROJECT, project.getScope());
        assertEquals("ENV_001", global.getKey());
        assertEquals("ENV_002", project.getKey());
        assertEquals(2, service.list().size());
    }

    @Test
    @DisplayName("用途说明是必填项")
    void requiresPurposeDescription() {
        assertThrows(DomainException.class, () -> service.create(new SaveEnvironmentCommand(
                EnvironmentScope.GLOBAL, null, "value", List.of(), "  ", false)));
    }

    @Test
    @DisplayName("项目级环境必须指向真实项目")
    void rejectsUnknownProject() {
        assertThrows(EntityNotFoundException.class,
                () -> service.create(command(EnvironmentScope.PROJECT, ProjectId.of(99L))));
        assertTrue(repository.entries.isEmpty());
    }

    @Test
    @DisplayName("修改后可以改变归属，并保留自动生成的键")
    void updatesScopeAndValidatesAgain() {
        EnvironmentEntry first = service.create(command(EnvironmentScope.GLOBAL, null));
        service.create(command(EnvironmentScope.PROJECT, ProjectId.of(1L)));

        service.update(first.getId(), new SaveEnvironmentCommand(
                EnvironmentScope.PROJECT, ProjectId.of(2L), "db-two",
                List.of("数据库"), "项目二数据库", true));

        assertEquals(ProjectId.of(2L), first.getProjectId());
        assertEquals("ENV_001", first.getKey());
        assertThrows(EntityNotFoundException.class, () -> service.update(first.getId(),
                new SaveEnvironmentCommand(EnvironmentScope.PROJECT, ProjectId.of(99L),
                        "x", List.of(), "项目二数据库", false)));
    }

    @Test
    @DisplayName("删除后仓储查不到")
    void deletesEntry() {
        EnvironmentEntry entry = service.create(command(EnvironmentScope.GLOBAL, null));

        service.delete(entry.getId());

        assertFalse(repository.findById(entry.getId()).isPresent());
    }

    private static SaveEnvironmentCommand command(EnvironmentScope scope, ProjectId projectId) {
        return new SaveEnvironmentCommand(
                scope, projectId, "value", List.of("测试"), "数据库连接", false);
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
