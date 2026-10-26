-- 版本级需求评审批次：一次提交形成一轮，任一条驳回即整轮失败。
CREATE TABLE `requirement_review_round` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `version_id`    BIGINT      NOT NULL,
    `round_no`      INT         NOT NULL,
    `status`        VARCHAR(16) NOT NULL,
    `submitted_by`  BIGINT      NOT NULL,
    `submitted_at`  DATETIME(3) NOT NULL,
    `completed_at`  DATETIME(3) DEFAULT NULL,
    `items_json`    JSON        NOT NULL,
    `created_at`    DATETIME(3) NOT NULL,
    `updated_at`    DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_requirement_review_round_version_no` (`version_id`, `round_no`),
    KEY `idx_requirement_review_round_latest` (`version_id`, `round_no`),
    CONSTRAINT `fk_requirement_review_round_version`
        FOREIGN KEY (`version_id`) REFERENCES `product_version` (`id`),
    CONSTRAINT `fk_requirement_review_round_submitted_by`
        FOREIGN KEY (`submitted_by`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '版本级需求评审批次';
