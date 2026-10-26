-- 立项审批材料：会议纪要正文与附件。
ALTER TABLE `initiative`
    ADD COLUMN `meeting_minutes` MEDIUMTEXT DEFAULT NULL AFTER `risk_text`;

CREATE TABLE `initiative_attachment` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `initiative_id`  BIGINT       NOT NULL,
    `filename`       VARCHAR(255) NOT NULL,
    `content_type`   VARCHAR(128) NOT NULL,
    `size_bytes`     BIGINT       NOT NULL,
    `storage_path`   VARCHAR(512) NOT NULL,
    `uploaded_by`    BIGINT       NOT NULL,
    `created_at`     DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_initiative_attachment_initiative` (`initiative_id`, `id`),
    CONSTRAINT `fk_initiative_attachment_initiative`
        FOREIGN KEY (`initiative_id`) REFERENCES `initiative` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_initiative_attachment_uploaded_by`
        FOREIGN KEY (`uploaded_by`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '立项会议纪要附件';
