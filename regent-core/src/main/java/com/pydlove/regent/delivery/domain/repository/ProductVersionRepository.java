package com.pydlove.regent.delivery.domain.repository;

import com.pydlove.regent.delivery.domain.model.ProductVersion;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.List;
import java.util.Optional;

/** 产品版本仓储。 */
public interface ProductVersionRepository {

    Optional<ProductVersion> findById(ProductVersionId id);

    List<ProductVersion> findByProject(ProjectId projectId);

    boolean existsByProjectAndVersionNo(ProjectId projectId, String versionNo);

    void save(ProductVersion version);

    void update(ProductVersion version);

    void delete(ProductVersionId id);
}
