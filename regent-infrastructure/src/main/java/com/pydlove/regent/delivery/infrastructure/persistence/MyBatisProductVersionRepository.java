package com.pydlove.regent.delivery.infrastructure.persistence;

import com.pydlove.regent.delivery.domain.model.ProductVersion;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.delivery.domain.model.ProductVersionStatus;
import com.pydlove.regent.delivery.domain.repository.ProductVersionRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 产品版本仓储的 MyBatis 实现。 */
@Repository
public class MyBatisProductVersionRepository implements ProductVersionRepository {

    private final ProductVersionMapper mapper;

    public MyBatisProductVersionRepository(ProductVersionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ProductVersion> findById(ProductVersionId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public List<ProductVersion> findByProject(ProjectId projectId) {
        return mapper.selectByProject(projectId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsByProjectAndVersionNo(ProjectId projectId, String versionNo) {
        return mapper.countByProjectAndVersionNo(projectId.value(), versionNo) > 0;
    }

    @Override
    public void save(ProductVersion version) {
        ProductVersionData data = toData(version);
        if (mapper.insert(data) != 1) {
            throw new DomainException("产品版本新增失败");
        }
        version.assignIdentity(ProductVersionId.of(data.getId()));
    }

    @Override
    public void update(ProductVersion version) {
        if (mapper.update(toData(version)) != 1) {
            throw new DomainException("产品版本更新失败：" + version.getId().value());
        }
    }

    private ProductVersion toDomain(ProductVersionData data) {
        return ProductVersion.restore(
                ProductVersionId.of(data.getId()),
                ProjectId.of(data.getProjectId()),
                data.getVersionNo(),
                data.getName(),
                data.getDescription(),
                ProductVersionStatus.valueOf(data.getStatus()),
                UserId.of(data.getCreatedBy()),
                DeliveryTime.fromDb(data.getCreatedAt()),
                DeliveryTime.fromDb(data.getUpdatedAt()));
    }

    private ProductVersionData toData(ProductVersion version) {
        ProductVersionData data = new ProductVersionData();
        data.setId(version.getId() == null ? null : version.getId().value());
        data.setProjectId(version.getProjectId().value());
        data.setVersionNo(version.getVersionNo());
        data.setName(version.getName());
        data.setDescription(version.getDescription());
        data.setStatus(version.getStatus().name());
        data.setCreatedBy(version.getCreatedBy().value());
        data.setCreatedAt(DeliveryTime.toDb(version.getCreatedAt()));
        data.setUpdatedAt(DeliveryTime.toDb(version.getUpdatedAt()));
        return data;
    }
}
