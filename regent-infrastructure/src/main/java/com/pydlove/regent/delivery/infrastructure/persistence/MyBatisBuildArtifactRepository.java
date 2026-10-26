package com.pydlove.regent.delivery.infrastructure.persistence;

import com.pydlove.regent.delivery.domain.model.BuildArtifact;
import com.pydlove.regent.delivery.domain.model.BuildArtifactId;
import com.pydlove.regent.delivery.domain.repository.BuildArtifactRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisBuildArtifactRepository implements BuildArtifactRepository {

    private final BuildArtifactMapper mapper;

    public MyBatisBuildArtifactRepository(BuildArtifactMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<BuildArtifact> findById(BuildArtifactId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(data -> BuildArtifact.restore(
                BuildArtifactId.of(data.getId()),
                ProjectId.of(data.getProjectId()),
                data.getVersion(),
                data.getGitCommit(),
                data.getDigest(),
                data.getSizeBytes() == null ? 0 : data.getSizeBytes(),
                data.getStorageRef(),
                UserId.of(data.getBuiltBy()),
                DeliveryTime.fromDb(data.getBuiltAt())));
    }

    @Override
    public void save(BuildArtifact artifact) {
        BuildArtifactData data = new BuildArtifactData();
        data.setProjectId(artifact.getProjectId().value());
        data.setVersion(artifact.getVersion());
        data.setGitCommit(artifact.getGitCommit());
        data.setDigest(artifact.getDigest());
        data.setSizeBytes(artifact.getSizeBytes());
        data.setStorageRef(artifact.getStorageRef());
        data.setBuiltBy(artifact.getBuiltBy().value());
        data.setBuiltAt(DeliveryTime.toDb(artifact.getBuiltAt()));
        if (mapper.insert(data) != 1) throw new DomainException("构建制品新增失败");
        artifact.assignIdentity(BuildArtifactId.of(data.getId()));
    }
}
