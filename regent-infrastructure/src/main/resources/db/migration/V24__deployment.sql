-- -------------------------------------------------------------------------------------
-- 部署配置与执行记录。
--
-- 部署脚本属于配置，跟测试用例一样直接存库：它需要跟服务器信息和历史执行一起备份、
-- 迁移和审计。密码不落库：密码认证只在发起执行时使用一次，避免部署配置成为凭据仓库。
-- -------------------------------------------------------------------------------------
CREATE TABLE `deployment` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT,
    `name`              VARCHAR(128)  NOT NULL,
    `host`              VARCHAR(255)  NOT NULL,
    `port`              INT           NOT NULL,
    `ssh_username`      VARCHAR(32)   NOT NULL,
    `auth_type`         VARCHAR(16)   NOT NULL COMMENT 'KEY/PASSWORD',
    `remote_directory`  VARCHAR(512)  NOT NULL COMMENT '远程脚本工作目录，允许 ~ 或绝对路径',
    `script_filename`   VARCHAR(255)  NOT NULL,
    `script_content`    MEDIUMTEXT    NOT NULL COMMENT 'UTF-8 部署脚本，执行时通过 SSH stdin 传入',
    `script_hash`       CHAR(64)      NOT NULL COMMENT '脚本内容 SHA-256',
    `deleted`           TINYINT(1)    NOT NULL DEFAULT 0,
    `created_at`        DATETIME(3)   NOT NULL,
    `updated_at`        DATETIME(3)   NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_deployment_name` (`deleted`, `name`),
    KEY `idx_deployment_updated` (`deleted`, `updated_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '部署配置';

CREATE TABLE `deployment_run` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT,
    `deployment_id`     BIGINT        NOT NULL,
    `deployment_name`   VARCHAR(128)  NOT NULL,
    `host`              VARCHAR(255)  NOT NULL,
    `port`              INT           NOT NULL,
    `ssh_username`      VARCHAR(32)   NOT NULL,
    `auth_type`         VARCHAR(16)   NOT NULL,
    `remote_directory`  VARCHAR(512)  NOT NULL,
    `script_filename`   VARCHAR(255)  NOT NULL,
    `script_content`    MEDIUMTEXT    NOT NULL,
    `script_hash`       CHAR(64)      NOT NULL,
    `status`            VARCHAR(16)   NOT NULL COMMENT 'QUEUED/RUNNING/SUCCESS/FAILED/TIMEOUT/INTERRUPTED',
    `exit_code`         INT           DEFAULT NULL,
    `summary`           VARCHAR(1024) DEFAULT NULL,
    `queued_at`         DATETIME(3)   NOT NULL,
    `started_at`        DATETIME(3)   DEFAULT NULL,
    `finished_at`       DATETIME(3)   DEFAULT NULL,
    `created_at`        DATETIME(3)   NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_deployment_run_deployment` (`deployment_id`, `id`),
    KEY `idx_deployment_run_status` (`status`),
    CONSTRAINT `fk_deployment_run_deployment`
        FOREIGN KEY (`deployment_id`) REFERENCES `deployment` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '部署执行记录';
