package com.pydlove.regent.deployment.infrastructure.credential;

import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.deployment.domain.port.DeploymentCredentialStore;
import com.pydlove.regent.deployment.infrastructure.persistence.DeploymentCredentialData;
import com.pydlove.regent.deployment.infrastructure.persistence.DeploymentCredentialMapper;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/** 本机 AES-GCM 凭据存储。主密钥只存在 {@code regent.home} 下。 */
public class LocalDeploymentCredentialStore implements DeploymentCredentialStore {

    private final DeploymentCredentialMapper mapper;
    private final AesGcmDeploymentCredentialCipher cipher;

    public LocalDeploymentCredentialStore(DeploymentCredentialMapper mapper, Path keyFile) {
        this.mapper = mapper;
        this.cipher = new AesGcmDeploymentCredentialCipher(keyFile);
    }

    @Override
    public void save(DeploymentId deploymentId, String password) {
        LocalDateTime now = LocalDateTime.ofInstant(
                Instant.now().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
        DeploymentCredentialData data = new DeploymentCredentialData();
        data.setDeploymentId(deploymentId.value());
        data.setCiphertext(cipher.encrypt(deploymentId.value(), password));
        data.setCreatedAt(now);
        data.setUpdatedAt(now);
        mapper.upsert(data);
    }

    @Override
    public boolean exists(DeploymentId deploymentId) {
        return mapper.countByDeploymentId(deploymentId.value()) > 0;
    }

    @Override
    public Optional<String> find(DeploymentId deploymentId) {
        return Optional.ofNullable(mapper.selectCiphertext(deploymentId.value()))
                .map(ciphertext -> cipher.decrypt(deploymentId.value(), ciphertext));
    }

    @Override
    public void delete(DeploymentId deploymentId) {
        mapper.deleteByDeploymentId(deploymentId.value());
    }
}
