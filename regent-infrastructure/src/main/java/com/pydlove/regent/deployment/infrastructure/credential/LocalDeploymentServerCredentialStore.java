package com.pydlove.regent.deployment.infrastructure.credential;

import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.deployment.domain.port.DeploymentServerCredentialStore;
import com.pydlove.regent.deployment.infrastructure.persistence.DeploymentServerCredentialData;
import com.pydlove.regent.deployment.infrastructure.persistence.DeploymentServerCredentialMapper;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/** 部署服务器密码的本机 AES-GCM 凭据存储。 */
public class LocalDeploymentServerCredentialStore implements DeploymentServerCredentialStore {

    private final DeploymentServerCredentialMapper mapper;
    private final AesGcmDeploymentCredentialCipher cipher;

    public LocalDeploymentServerCredentialStore(DeploymentServerCredentialMapper mapper,
                                                Path keyFile) {
        this.mapper = mapper;
        this.cipher = new AesGcmDeploymentCredentialCipher(keyFile);
    }

    @Override
    public void save(DeploymentServerId serverId, String password) {
        LocalDateTime now = LocalDateTime.ofInstant(
                Instant.now().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
        DeploymentServerCredentialData data = new DeploymentServerCredentialData();
        data.setServerId(serverId.value());
        data.setAadId(serverId.value());
        data.setCiphertext(cipher.encrypt(serverId.value(), password));
        data.setCreatedAt(now);
        data.setUpdatedAt(now);
        mapper.upsert(data);
    }

    @Override
    public boolean exists(DeploymentServerId serverId) {
        return mapper.countByServerId(serverId.value()) > 0;
    }

    @Override
    public Optional<String> find(DeploymentServerId serverId) {
        return Optional.ofNullable(mapper.selectByServerId(serverId.value()))
                .map(data -> cipher.decrypt(data.getAadId(), data.getCiphertext()));
    }

    @Override
    public void delete(DeploymentServerId serverId) {
        mapper.deleteByServerId(serverId.value());
    }
}
