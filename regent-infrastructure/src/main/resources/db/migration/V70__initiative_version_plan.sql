-- 版本计划：项目负责人在版本立项阶段维护排期、里程碑、角色投入和人天估算。
CREATE TABLE `initiative_plan` (
    `initiative_id`   BIGINT       NOT NULL,
    `plan_start_date` DATE         NOT NULL,
    `plan_end_date`   DATE         NOT NULL,
    `milestones`      TEXT         DEFAULT NULL,
    `updated_by`      BIGINT       NOT NULL,
    `updated_at`      DATETIME(3)  NOT NULL,
    PRIMARY KEY (`initiative_id`),
    CONSTRAINT `fk_initiative_plan_initiative`
        FOREIGN KEY (`initiative_id`) REFERENCES `initiative` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_initiative_plan_updated_by`
        FOREIGN KEY (`updated_by`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '版本计划主表';

CREATE TABLE `initiative_plan_item` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `initiative_id`  BIGINT       NOT NULL,
    `name`           VARCHAR(160) NOT NULL,
    `role_code`      VARCHAR(32)  NOT NULL,
    `start_date`     DATE         NOT NULL,
    `end_date`       DATE         NOT NULL,
    `headcount`      INT          NOT NULL,
    `sort_order`     INT          NOT NULL,
    `created_at`     DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_initiative_plan_item_sort` (`initiative_id`, `sort_order`, `id`),
    CONSTRAINT `fk_initiative_plan_item_plan`
        FOREIGN KEY (`initiative_id`) REFERENCES `initiative_plan` (`initiative_id`)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '版本计划工作项';
