-- 需求评审改为线下评审后登记结果：线上只保留评审记录与会议纪要证据。
-- 原线上逐条评审状态退回待需求评审。
UPDATE `requirement`
SET `pool_status` = 'POOL',
    `review_comment` = NULL,
    `reviewed_by` = NULL,
    `reviewed_at` = NULL,
    `updated_at` = UTC_TIMESTAMP(3)
WHERE `pool_status` = 'IN_REVIEW'
  AND `deleted` = 0;

CREATE TABLE `requirement_review_record` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `version_id`        BIGINT       NOT NULL,
    `review_no`         INT          NOT NULL,
    `review_date`       DATE         NOT NULL,
    `participants`      VARCHAR(512) DEFAULT NULL,
    `conclusion`        VARCHAR(512) NOT NULL,
    `minutes`           MEDIUMTEXT   DEFAULT NULL,
    `requirements_json` JSON         NOT NULL,
    `reviewed_by`       BIGINT       NOT NULL,
    `created_at`        DATETIME(3)  NOT NULL,
    `updated_at`        DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_requirement_review_record_version_no` (`version_id`, `review_no`),
    KEY `idx_requirement_review_record_version` (`version_id`, `review_no`),
    CONSTRAINT `fk_requirement_review_record_version`
        FOREIGN KEY (`version_id`) REFERENCES `product_version` (`id`),
    CONSTRAINT `fk_requirement_review_record_reviewed_by`
        FOREIGN KEY (`reviewed_by`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '线下需求评审记录';

CREATE TABLE `requirement_review_attachment` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `record_id`     BIGINT       NOT NULL,
    `filename`      VARCHAR(255) NOT NULL,
    `content_type`  VARCHAR(128) NOT NULL,
    `size_bytes`    BIGINT       NOT NULL,
    `storage_path`  VARCHAR(512) NOT NULL,
    `uploaded_by`   BIGINT       NOT NULL,
    `created_at`    DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_requirement_review_attachment_record` (`record_id`, `id`),
    CONSTRAINT `fk_requirement_review_attachment_record`
        FOREIGN KEY (`record_id`) REFERENCES `requirement_review_record` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_requirement_review_attachment_uploaded_by`
        FOREIGN KEY (`uploaded_by`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '需求评审会议纪要附件';
