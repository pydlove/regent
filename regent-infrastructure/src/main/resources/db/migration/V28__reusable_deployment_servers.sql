-- 将部署配置中的主机连接抽取为可复用服务器资源。
-- 迁移必须保留旧密码密文：AES-GCM 的 AAD 沿用原部署 ID，凭据表用 aad_id 记录该绑定。

CREATE TABLE `deployment_server` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `project_id`     BIGINT        NOT NULL,
    `name`           VARCHAR(128)  NOT NULL,
    `host`           VARCHAR(255)  NOT NULL,
    `port`           INT           NOT NULL,
    `ssh_username`   VARCHAR(32)   NOT NULL,
    `auth_type`      VARCHAR(16)   NOT NULL COMMENT 'KEY/PASSWORD',
    `migration_key`  CHAR(64)      DEFAULT NULL COMMENT '仅迁移期用于合并相同连接',
    `deleted`        TINYINT(1)    NOT NULL DEFAULT 0,
    `created_at`     DATETIME(3)   NOT NULL,
    `updated_at`     DATETIME(3)   NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_deployment_server_migration_key` (`migration_key`),
    KEY `idx_deployment_server_project` (`project_id`, `deleted`, `updated_at`),
    CONSTRAINT `fk_deployment_server_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '可复用部署服务器';

CREATE TABLE `deployment_server_credential` (
    `server_id`   BIGINT      NOT NULL,
    `aad_id`      BIGINT      NOT NULL COMMENT '旧密文使用原部署 ID 作为 AAD；新服务器使用服务器 ID',
    `ciphertext`  MEDIUMTEXT  NOT NULL,
    `created_at`  DATETIME(3) NOT NULL,
    `updated_at`  DATETIME(3) NOT NULL,
    PRIMARY KEY (`server_id`),
    CONSTRAINT `fk_deployment_server_credential_server`
        FOREIGN KEY (`server_id`) REFERENCES `deployment_server` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '部署服务器 SSH 密码密文';

INSERT INTO `deployment_server`
    (`project_id`, `name`, `host`, `port`, `ssh_username`, `auth_type`,
     `migration_key`, `deleted`, `created_at`, `updated_at`)
SELECT
    d.project_id,
    CONCAT('服务器 ', MIN(d.id)),
    d.host,
    d.port,
    d.ssh_username,
    d.auth_type,
    SHA2(CONCAT_WS('|', d.project_id, d.host, d.port, d.ssh_username, d.auth_type,
                   COALESCE(c.ciphertext, '')), 256),
    0,
    MIN(d.created_at),
    MAX(d.updated_at)
FROM deployment d
LEFT JOIN deployment_credential c ON c.deployment_id = d.id
GROUP BY d.project_id, d.host, d.port, d.ssh_username, d.auth_type, c.ciphertext;

ALTER TABLE `deployment`
    ADD COLUMN `server_id` BIGINT DEFAULT NULL AFTER `project_id`;

UPDATE deployment d
LEFT JOIN deployment_credential c ON c.deployment_id = d.id
JOIN deployment_server s
  ON s.migration_key = SHA2(CONCAT_WS('|', d.project_id, d.host, d.port,
                                      d.ssh_username, d.auth_type,
                                      COALESCE(c.ciphertext, '')), 256)
SET d.server_id = s.id;

INSERT INTO `deployment_server_credential`
    (`server_id`, `aad_id`, `ciphertext`, `created_at`, `updated_at`)
SELECT s.id, MIN(d.id), c.ciphertext, MIN(c.created_at), MAX(c.updated_at)
FROM deployment d
JOIN deployment_credential c ON c.deployment_id = d.id
JOIN deployment_server s
  ON s.migration_key = SHA2(CONCAT_WS('|', d.project_id, d.host, d.port,
                                      d.ssh_username, d.auth_type, c.ciphertext), 256)
GROUP BY s.id, c.ciphertext
ON DUPLICATE KEY UPDATE
    ciphertext = VALUES(ciphertext),
    updated_at = GREATEST(updated_at, VALUES(updated_at));

ALTER TABLE `deployment`
    MODIFY COLUMN `server_id` BIGINT NOT NULL,
    ADD KEY `idx_deployment_server` (`server_id`, `deleted`),
    ADD CONSTRAINT `fk_deployment_server`
        FOREIGN KEY (`server_id`) REFERENCES `deployment_server` (`id`);

DROP TABLE `deployment_credential`;

ALTER TABLE `deployment`
    DROP COLUMN `host`,
    DROP COLUMN `port`,
    DROP COLUMN `ssh_username`,
    DROP COLUMN `auth_type`;

ALTER TABLE `deployment_server`
    DROP INDEX `uk_deployment_server_migration_key`,
    DROP COLUMN `migration_key`;
