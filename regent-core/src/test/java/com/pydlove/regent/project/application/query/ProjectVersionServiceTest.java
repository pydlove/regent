package com.pydlove.regent.project.application.query;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectSummaryStatus;
import com.pydlove.regent.project.domain.model.ProjectVersionSource;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectVersionServiceTest {

    @Test
    void GitTag优先于备用版本() {
        ProjectVersionView view = service(ProjectVersionSource.GIT_TAG, "v1.0.0", "v1.2.3")
                .resolve(ProjectId.of(1));

        assertTrue(view.available());
        assertEquals("v1.2.3", view.version());
        assertEquals("GIT_TAG", view.source());
    }

    @Test
    void 没有GitTag时使用备用版本() {
        ProjectVersionView view = service(ProjectVersionSource.GIT_TAG, "v1.0.0", null)
                .resolve(ProjectId.of(1));

        assertTrue(view.available());
        assertEquals("v1.0.0", view.version());
        assertEquals("FALLBACK", view.source());
    }

    @Test
    void 手动模式直接使用固定版本() {
        ProjectVersionView view = service(ProjectVersionSource.MANUAL, "2026.10.22", "v9.9.9")
                .resolve(ProjectId.of(1));

        assertTrue(view.available());
        assertEquals("2026.10.22", view.version());
        assertEquals("MANUAL", view.source());
    }

    @Test
    void 没有Tag和备用版本时返回不可用() {
        ProjectVersionView view = service(ProjectVersionSource.GIT_TAG, null, null)
                .resolve(ProjectId.of(1));

        assertFalse(view.available());
        assertEquals("NONE", view.source());
    }

    private static ProjectVersionService service(ProjectVersionSource source,
                                                 String fallback,
                                                 String tag) {
        ProjectProfile profile = new ProjectProfile(
                ProjectId.of(1),
                null,
                "regent",
                "Regent",
                "/tmp/regent",
                DecisionLevel.L1,
                true,
                ProjectSummaryStatus.NOT_GENERATED,
                null,
                null,
                null,
                source,
                fallback);
        ProjectProfileRepository repository = new ProjectProfileRepository() {
            @Override
            public Optional<ProjectProfile> findById(ProjectId id) {
                return Optional.of(profile);
            }

            @Override
            public Optional<ProjectProfile> findByCode(String code) {
                return Optional.empty();
            }

            @Override
            public List<ProjectProfile> findAll() {
                return List.of(profile);
            }
        };
        return new ProjectVersionService(repository, path -> Optional.ofNullable(tag));
    }
}
