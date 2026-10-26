-- 研发交付全流程：立项、需求交付、构建制品、发布和阶段门禁。

CREATE TABLE `initiative` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`        BIGINT       NOT NULL,
    `created_by`        BIGINT       NOT NULL,
    `title`             VARCHAR(160) NOT NULL,
    `business_goal`     TEXT         NOT NULL,
    `scope_text`        TEXT         DEFAULT NULL,
    `out_of_scope_text` TEXT         DEFAULT NULL,
    `success_metrics`   TEXT         DEFAULT NULL,
    `status`            VARCHAR(32)  NOT NULL,
    `submitted_at`      DATETIME(3)  DEFAULT NULL,
    `approved_at`       DATETIME(3)  DEFAULT NULL,
    `rejected_at`       DATETIME(3)  DEFAULT NULL,
    `created_at`        DATETIME(3)  NOT NULL,
    `updated_at`        DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_initiative_project_status` (`project_id`, `status`, `id`),
    CONSTRAINT `fk_initiative_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
    CONSTRAINT `fk_initiative_created_by`
        FOREIGN KEY (`created_by`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '研发立项单';

CREATE TABLE `requirement_delivery` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `initiative_id`   BIGINT       NOT NULL,
    `project_id`      BIGINT       NOT NULL,
    `requirement_id`  BIGINT       NOT NULL,
    `created_by`      BIGINT       NOT NULL,
    `stage`           VARCHAR(32)  NOT NULL,
    `status`          VARCHAR(32)  NOT NULL,
    `design_summary`  TEXT         DEFAULT NULL,
    `design_content`  MEDIUMTEXT   DEFAULT NULL,
    `created_at`      DATETIME(3)  NOT NULL,
    `updated_at`      DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_requirement_delivery_project` (`project_id`, `stage`, `id`),
    KEY `idx_requirement_delivery_requirement` (`requirement_id`, `status`),
    CONSTRAINT `fk_requirement_delivery_initiative`
        FOREIGN KEY (`initiative_id`) REFERENCES `initiative` (`id`),
    CONSTRAINT `fk_requirement_delivery_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
    CONSTRAINT `fk_requirement_delivery_requirement`
        FOREIGN KEY (`requirement_id`) REFERENCES `requirement` (`id`),
    CONSTRAINT `fk_requirement_delivery_created_by`
        FOREIGN KEY (`created_by`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '需求研发交付单';

CREATE TABLE `build_artifact` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`   BIGINT       NOT NULL,
    `version`      VARCHAR(64)  NOT NULL,
    `git_commit`   VARCHAR(64)  NOT NULL,
    `digest`       VARCHAR(128) DEFAULT NULL,
    `size_bytes`   BIGINT       NOT NULL DEFAULT 0,
    `storage_ref`  VARCHAR(1000) DEFAULT NULL,
    `built_by`     BIGINT       NOT NULL,
    `built_at`     DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_build_artifact_project_version` (`project_id`, `version`, `id`),
    CONSTRAINT `fk_build_artifact_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
    CONSTRAINT `fk_build_artifact_built_by`
        FOREIGN KEY (`built_by`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '不可变构建制品';

CREATE TABLE `release` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`        BIGINT       NOT NULL,
    `version`           VARCHAR(64)  NOT NULL,
    `attempt_no`        INT          NOT NULL,
    `build_artifact_id` BIGINT       NOT NULL,
    `stage`             VARCHAR(32)  NOT NULL,
    `status`            VARCHAR(32)  NOT NULL,
    `release_notes`     TEXT         DEFAULT NULL,
    `rollback_plan`     TEXT         NOT NULL,
    `created_at`        DATETIME(3)  NOT NULL,
    `updated_at`        DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_release_version_attempt` (`project_id`, `version`, `attempt_no`),
    KEY `idx_release_project_stage` (`project_id`, `stage`, `id`),
    CONSTRAINT `fk_release_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
    CONSTRAINT `fk_release_artifact`
        FOREIGN KEY (`build_artifact_id`) REFERENCES `build_artifact` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '版本发布轮次';

CREATE TABLE `release_item` (
    `release_id`              BIGINT      NOT NULL,
    `requirement_delivery_id` BIGINT      NOT NULL,
    `created_at`              DATETIME(3) NOT NULL,
    PRIMARY KEY (`release_id`, `requirement_delivery_id`),
    KEY `idx_release_item_delivery` (`requirement_delivery_id`, `release_id`),
    CONSTRAINT `fk_release_item_release`
        FOREIGN KEY (`release_id`) REFERENCES `release` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_release_item_delivery`
        FOREIGN KEY (`requirement_delivery_id`) REFERENCES `requirement_delivery` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '发布包含的需求交付单';

CREATE TABLE `delivery_gate` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `scope_type`  VARCHAR(32)  NOT NULL,
    `scope_id`    BIGINT       NOT NULL,
    `gate_type`   VARCHAR(48)  NOT NULL,
    `decision`    VARCHAR(32)  NOT NULL,
    `comment`     TEXT         DEFAULT NULL,
    `decided_by`  BIGINT       NOT NULL,
    `decided_at`  DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_delivery_gate_scope` (`scope_type`, `scope_id`, `id`),
    CONSTRAINT `fk_delivery_gate_decided_by`
        FOREIGN KEY (`decided_by`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '阶段门禁与审批事实';
