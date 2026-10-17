package com.pydlove.regent.plugin.infrastructure.persistence;

import com.pydlove.regent.plugin.domain.model.Plugin;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.plugin.domain.model.PluginVersion;
import com.pydlove.regent.plugin.domain.repository.PluginRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** {@link PluginRepository} 的 MyBatis 实现。 */
@Repository
public class MyBatisPluginRepository implements PluginRepository {

    private final PluginMapper mapper;

    public MyBatisPluginRepository(PluginMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Plugin> findById(PluginId id) {
        return load(mapper.selectById(id.value()));
    }

    @Override
    public Optional<Plugin> findByIdForUpdate(PluginId id) {
        return load(mapper.selectByIdForUpdate(id.value()));
    }

    @Override
    public Optional<PluginVersion> findVersion(PluginId id, int revision) {
        return Optional.ofNullable(mapper.selectVersion(id.value(), revision))
                .map(PluginDataMapper::toVersion);
    }

    @Override
    public List<Plugin> findAll() {
        return mapper.selectAll().stream()
                .map(row -> load(row).orElseThrow())
                .toList();
    }

    @Override
    public boolean existsByName(String name, PluginId excludingId) {
        return mapper.countByName(name, excludingId == null ? null : excludingId.value()) > 0;
    }

    @Override
    public void save(Plugin plugin) {
        PluginData data = PluginDataMapper.toData(plugin);
        try {
            if (plugin.getId() == null) {
                if (mapper.insertPlugin(data) != 1) {
                    throw new DomainException("插件新增失败：" + plugin.getName());
                }
                plugin.assignIdentity(PluginId.of(data.getId()));
                insertCurrentVersion(plugin);
                return;
            }
            if (mapper.updatePlugin(data) != 1) {
                throw new DomainException("插件更新失败或不存在：" + plugin.getId());
            }
            insertCurrentVersion(plugin);
        } catch (DuplicateKeyException e) {
            throw new DomainException("已存在同名插件：" + plugin.getName(), e);
        }
    }

    private void insertCurrentVersion(Plugin plugin) {
        PluginVersion version = plugin.getCurrentVersion();
        if (mapper.selectVersion(plugin.getId().value(), version.revision()) != null) {
            return;
        }
        if (mapper.insertVersion(PluginDataMapper.toVersionRow(plugin.getId(), version)) != 1) {
            throw new DomainException("插件版本新增失败：" + plugin.getName()
                    + " v" + version.revision());
        }
    }

    private Optional<Plugin> load(PluginRow row) {
        if (row == null) {
            return Optional.empty();
        }
        PluginVersionRow version = mapper.selectVersion(row.getId(), row.getCurrentRevision());
        if (version == null) {
            throw new DomainException("插件当前版本缺失：" + row.getId() + " v" + row.getCurrentRevision());
        }
        return Optional.of(PluginDataMapper.toPlugin(row, version));
    }
}
