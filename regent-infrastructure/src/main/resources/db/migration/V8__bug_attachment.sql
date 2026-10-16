-- -------------------------------------------------------------------------------------
-- Bug 单图片附件 —— 人工录入缺陷时可以贴复现截图、报错现场或界面证据。
--
-- 与 task_attachment 分表而不是做多态外键：Bug 与 Task 是两个聚合，附件归属不同；
-- 多态外键在 MySQL 里建不出真正的约束，最终只能靠应用代码兜底。
-- -------------------------------------------------------------------------------------
CREATE TABLE `bug_attachment` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `bug_id`       BIGINT       NOT NULL,
    `filename`     VARCHAR(255) NOT NULL COMMENT '用户的原始文件名，仅用于展示',
    `content_type` VARCHAR(128) NOT NULL COMMENT 'image/png 等白名单类型',
    `size_bytes`   BIGINT       NOT NULL,
    `storage_path` VARCHAR(512) NOT NULL COMMENT '平台侧绝对路径',
    `created_at`   DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_bug_attachment_bug` (`bug_id`),
    CONSTRAINT `fk_bug_attachment_bug` FOREIGN KEY (`bug_id`) REFERENCES `bug` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Bug 单图片附件';
