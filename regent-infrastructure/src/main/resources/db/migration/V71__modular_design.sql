-- 概要设计与模块详细设计：技术负责人上传概要设计，研发分别提交自己模块的详细设计。
ALTER TABLE `requirement_delivery`
    ADD COLUMN `outline_design_summary` TEXT DEFAULT NULL AFTER `design_content`,
    ADD COLUMN `outline_design_content` MEDIUMTEXT DEFAULT NULL AFTER `outline_design_summary`,
    ADD COLUMN `outline_design_submitted_at` DATETIME(3) DEFAULT NULL AFTER `outline_design_content`,
    ADD COLUMN `outline_design_submitted_by` BIGINT DEFAULT NULL AFTER `outline_design_submitted_at`,
    ADD CONSTRAINT `fk_requirement_delivery_outline_submitted_by`
        FOREIGN KEY (`outline_design_submitted_by`) REFERENCES `app_user` (`id`);

CREATE TABLE `requirement_design_module` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `delivery_id`    BIGINT       NOT NULL,
    `name`           VARCHAR(160) NOT NULL,
    `description`    TEXT         DEFAULT NULL,
    `assignee_id`    BIGINT       NOT NULL,
    `status`         VARCHAR(32)  NOT NULL,
    `design_summary` TEXT         DEFAULT NULL,
    `design_content` MEDIUMTEXT   DEFAULT NULL,
    `created_by`     BIGINT       NOT NULL,
    `created_at`     DATETIME(3)  NOT NULL,
    `updated_at`     DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_requirement_design_module_name` (`delivery_id`, `name`),
    KEY `idx_requirement_design_module_assignee` (`assignee_id`, `status`, `id`),
    CONSTRAINT `fk_requirement_design_module_delivery`
        FOREIGN KEY (`delivery_id`) REFERENCES `requirement_delivery` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_requirement_design_module_assignee`
        FOREIGN KEY (`assignee_id`) REFERENCES `app_user` (`id`),
    CONSTRAINT `fk_requirement_design_module_created_by`
        FOREIGN KEY (`created_by`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '需求详细设计模块及研发负责人';

CREATE TABLE `requirement_design_attachment` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `delivery_id`   BIGINT       NOT NULL,
    `module_id`     BIGINT       DEFAULT NULL,
    `filename`      VARCHAR(255) NOT NULL,
    `content_type`  VARCHAR(128) NOT NULL,
    `size_bytes`    BIGINT       NOT NULL,
    `storage_path`  VARCHAR(512) NOT NULL,
    `uploaded_by`   BIGINT       NOT NULL,
    `created_at`    DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_requirement_design_attachment_delivery` (`delivery_id`, `module_id`, `id`),
    CONSTRAINT `fk_requirement_design_attachment_delivery`
        FOREIGN KEY (`delivery_id`) REFERENCES `requirement_delivery` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_requirement_design_attachment_module`
        FOREIGN KEY (`module_id`) REFERENCES `requirement_design_module` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_requirement_design_attachment_uploaded_by`
        FOREIGN KEY (`uploaded_by`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '概要设计与模块详细设计附件';
