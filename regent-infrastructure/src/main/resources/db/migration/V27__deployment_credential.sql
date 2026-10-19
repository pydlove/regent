-- SSH 密码与部署配置分开保存，避免密码进入部署聚合或查询接口。
-- ciphertext 由应用使用本机 0600 主密钥和 AES-GCM 加密，数据库里不出现明文。
CREATE TABLE `deployment_credential` (
    `deployment_id` BIGINT      NOT NULL,
    `ciphertext`    MEDIUMTEXT  NOT NULL,
    `created_at`    DATETIME(3) NOT NULL,
    `updated_at`    DATETIME(3) NOT NULL,
    PRIMARY KEY (`deployment_id`),
    CONSTRAINT `fk_deployment_credential_deployment`
        FOREIGN KEY (`deployment_id`) REFERENCES `deployment` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '部署 SSH 密码密文';
