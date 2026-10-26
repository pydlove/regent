-- =====================================================================================
-- V50：文件库分类与项目开发规范库
--
-- 1) 文件库增加 library_type，存量文件统一归为 GENERAL；
-- 2) 开发规范采用稳定条目与不可变版本，版本内容继续复用 file_library_document；
-- 3) 任务创建时写入 task_code_standard 快照，后续规范更新不影响历史任务；
-- 4) 归档只改状态，不物理删除，保证历史任务仍可追溯。
-- =====================================================================================

ALTER TABLE `file_library_document`
    ADD COLUMN `library_type` VARCHAR(32) NOT NULL DEFAULT 'GENERAL'
        COMMENT 'GENERAL/DEVELOPMENT_RULE/REQUIREMENT/DESIGN/TESTING'
        AFTER `project_id`,
    DROP INDEX `uk_file_library_project_hash`,
    ADD UNIQUE KEY `uk_file_library_project_type_hash`
        (`project_id`, `library_type`, `content_hash`),
    ADD KEY `idx_file_library_project_type_created`
        (`project_id`, `library_type`, `created_at`);


CREATE TABLE `code_standard` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`         BIGINT       NOT NULL,
    `name`               VARCHAR(160) NOT NULL,
    `description`        VARCHAR(512) DEFAULT NULL,
    `scope`              VARCHAR(32)  NOT NULL COMMENT 'GENERAL/FRONTEND/BACKEND/TESTING/SECURITY/DATABASE/DEPLOYMENT',
    `enabled`            TINYINT(1)   NOT NULL DEFAULT 1,
    `default_apply`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '新建任务时是否默认引用',
    `sort_order`         INT          NOT NULL DEFAULT 0,
    `archived`           TINYINT(1)   NOT NULL DEFAULT 0,
    `source_project_id`  BIGINT       DEFAULT NULL COMMENT '跨项目复制来源',
    `source_standard_id` BIGINT       DEFAULT NULL COMMENT '来源规范条目 ID',
    `source_version_id`  BIGINT       DEFAULT NULL COMMENT '来源规范版本 ID',
    `source_version_no`  INT          DEFAULT NULL COMMENT '来源版本号',
    `created_at`         DATETIME(3)  NOT NULL,
    `updated_at`         DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_code_standard_project` (`project_id`, `archived`, `sort_order`, `id`),
    KEY `idx_code_standard_source_project` (`source_project_id`),
    CONSTRAINT `fk_code_standard_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
    CONSTRAINT `fk_code_standard_source_project`
        FOREIGN KEY (`source_project_id`) REFERENCES `project` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目开发规范条目';


CREATE TABLE `code_standard_version` (
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT,
    `standard_id`         BIGINT      NOT NULL,
    `project_id`          BIGINT      NOT NULL,
    `version_no`          INT         NOT NULL,
    `library_document_id` BIGINT      NOT NULL,
    `content_hash`        CHAR(64)    NOT NULL,
    `created_at`          DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code_standard_version` (`standard_id`, `version_no`),
    KEY `idx_code_standard_version_document` (`library_document_id`),
    KEY `idx_code_standard_version_project` (`project_id`, `created_at`),
    CONSTRAINT `fk_code_standard_version_standard`
        FOREIGN KEY (`standard_id`) REFERENCES `code_standard` (`id`),
    CONSTRAINT `fk_code_standard_version_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
    CONSTRAINT `fk_code_standard_version_document`
        FOREIGN KEY (`library_document_id`) REFERENCES `file_library_document` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '开发规范不可变版本';


CREATE TABLE `task_code_standard` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`             BIGINT       NOT NULL,
    `standard_id`         BIGINT       NOT NULL,
    `standard_version_id` BIGINT       NOT NULL,
    `standard_name`       VARCHAR(160) NOT NULL,
    `version_no`          INT          NOT NULL,
    `filename`            VARCHAR(255) NOT NULL,
    `content_type`        VARCHAR(128) NOT NULL,
    `size_bytes`          BIGINT       NOT NULL,
    `content_hash`        CHAR(64)     NOT NULL,
    `storage_path`        VARCHAR(512) NOT NULL,
    `source_project_id`   BIGINT       DEFAULT NULL,
    `created_at`          DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_code_standard` (`task_id`, `standard_id`),
    KEY `idx_task_code_standard_version` (`standard_version_id`),
    CONSTRAINT `fk_task_code_standard_task`
        FOREIGN KEY (`task_id`) REFERENCES `task` (`id`),
    CONSTRAINT `fk_task_code_standard_standard`
        FOREIGN KEY (`standard_id`) REFERENCES `code_standard` (`id`),
    CONSTRAINT `fk_task_code_standard_version`
        FOREIGN KEY (`standard_version_id`) REFERENCES `code_standard_version` (`id`),
    CONSTRAINT `fk_task_code_standard_source_project`
        FOREIGN KEY (`source_project_id`) REFERENCES `project` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '任务开发规范版本快照';
