package com.pydlove.regent.delivery.application.query;

import com.pydlove.regent.delivery.domain.model.ProductVersion;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.delivery.domain.repository.ProductVersionRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.List;

/** 产品版本查询。 */
public class ProductVersionQueryService {

    private final ProductVersionRepository versionRepository;
    private final ProjectProfileRepository projectProfileRepository;

    public ProductVersionQueryService(ProductVersionRepository versionRepository,
                                      ProjectProfileRepository projectProfileRepository) {
        this.versionRepository = versionRepository;
        this.projectProfileRepository = projectProfileRepository;
    }

    public List<ProductVersion> list(ProjectId projectId, UserId actorId) {
        requireProject(projectId, actorId);
        return versionRepository.findByProject(projectId);
    }

    public ProductVersion get(ProductVersionId id, UserId actorId) {
        ProductVersion version = versionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "产品版本不存在：" + id.value()));
        requireProject(version.getProjectId(), actorId);
        return version;
    }

    private void requireProject(ProjectId projectId, UserId actorId) {
        projectProfileRepository.findById(projectId, actorId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "项目不存在：" + projectId.value()));
    }
}
