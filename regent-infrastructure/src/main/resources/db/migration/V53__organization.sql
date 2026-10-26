-- 组织是项目之上的业务租户。账号保持全局，同一账号可以加入多个组织。
-- 现有账号先各建一个个人组织，再把历史项目归入原负责人的个人组织，避免迁移丢数据。

CREATE TABLE `organization` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `code`        VARCHAR(64)  NOT NULL COMMENT '组织标识，全局唯一',
    `name`        VARCHAR(128) NOT NULL,
    `description` VARCHAR(512) DEFAULT NULL,
    `status`      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    `created_by`  BIGINT       NOT NULL,
    `created_at`  DATETIME(3)  NOT NULL,
    `updated_at`  DATETIME(3)  NOT NULL,
    `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_organization_code` (`code`),
    KEY `idx_organization_creator` (`created_by`),
    CONSTRAINT `fk_organization_creator`
        FOREIGN KEY (`created_by`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '组织';

CREATE TABLE `organization_member` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT,
    `organization_id` BIGINT      NOT NULL,
    `user_id`         BIGINT      NOT NULL,
    `role_code`       VARCHAR(32) NOT NULL COMMENT 'ORG_OWNER/ORG_ADMIN/PROJECT_MANAGER/ORG_MEMBER',
    `status`          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    `joined_at`       DATETIME(3) NOT NULL,
    `created_at`      DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_org_member_role` (`organization_id`, `user_id`, `role_code`),
    KEY `idx_org_member_user` (`user_id`, `organization_id`),
    CONSTRAINT `fk_org_member_organization`
        FOREIGN KEY (`organization_id`) REFERENCES `organization` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_org_member_user`
        FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '组织成员及组织级角色';

CREATE TABLE `organization_invitation` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `organization_id` BIGINT       NOT NULL,
    `email`           VARCHAR(254) NOT NULL,
    `role_code`       VARCHAR(32)  NOT NULL,
    `token_hash`      CHAR(64)     NOT NULL COMMENT '邀请明文令牌的 SHA-256',
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REVOKED/EXPIRED',
    `expires_at`      DATETIME(3)  NOT NULL,
    `accepted_at`     DATETIME(3)  DEFAULT NULL,
    `accepted_by`     BIGINT       DEFAULT NULL,
    `created_by`      BIGINT       NOT NULL,
    `created_at`      DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_org_invitation_token` (`token_hash`),
    KEY `idx_org_invitation_lookup` (`organization_id`, `email`, `status`),
    CONSTRAINT `fk_org_invitation_organization`
        FOREIGN KEY (`organization_id`) REFERENCES `organization` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_org_invitation_creator`
        FOREIGN KEY (`created_by`) REFERENCES `app_user` (`id`),
    CONSTRAINT `fk_org_invitation_accepted_by`
        FOREIGN KEY (`accepted_by`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '组织成员邀请';

CREATE TABLE `organization_audit_log` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT,
    `organization_id` BIGINT      NOT NULL,
    `actor_id`        BIGINT      NOT NULL,
    `action`          VARCHAR(64) NOT NULL,
    `target_type`     VARCHAR(32) NOT NULL,
    `target_id`       BIGINT      DEFAULT NULL,
    `detail_json`     JSON        DEFAULT NULL,
    `created_at`      DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_org_audit_time` (`organization_id`, `created_at`),
    CONSTRAINT `fk_org_audit_organization`
        FOREIGN KEY (`organization_id`) REFERENCES `organization` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_org_audit_actor`
        FOREIGN KEY (`actor_id`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '组织管理审计';

INSERT INTO `organization`
    (`code`, `name`, `description`, `status`, `created_by`, `created_at`, `updated_at`, `deleted`)
SELECT
    CONCAT('personal_', u.`id`),
    CONCAT(u.`display_name`, '的工作组织'),
    '历史账号自动迁移生成的个人组织',
    'ACTIVE',
    u.`id`,
    UTC_TIMESTAMP(3),
    UTC_TIMESTAMP(3),
    0
FROM `app_user` u
WHERE u.`id` IS NOT NULL;

INSERT INTO `organization_member`
    (`organization_id`, `user_id`, `role_code`, `status`, `joined_at`, `created_at`)
SELECT
    o.`id`, o.`created_by`, 'ORG_OWNER', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM `organization` o;

ALTER TABLE `project`
    ADD COLUMN `organization_id` BIGINT DEFAULT NULL AFTER `id`,
    ADD KEY `idx_project_organization` (`organization_id`, `deleted`);

UPDATE `project` p
JOIN `organization` o
  ON o.`created_by` = p.`owner_id`
 AND o.`code` = CONCAT('personal_', p.`owner_id`)
SET p.`organization_id` = o.`id`
WHERE p.`organization_id` IS NULL;

ALTER TABLE `project`
    ADD CONSTRAINT `fk_project_organization`
        FOREIGN KEY (`organization_id`) REFERENCES `organization` (`id`);

-- 项目标识从“全平台唯一”改为“组织内唯一”。不同组织可以各自拥有 regent。
ALTER TABLE `project`
    DROP INDEX `uk_project_code`,
    ADD UNIQUE KEY `uk_project_org_code` (`organization_id`, `code`);
