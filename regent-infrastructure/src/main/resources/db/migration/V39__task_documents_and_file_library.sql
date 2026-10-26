-- =====================================================================================
-- V39：任务文档与可复用文件库
--
-- 1) file_library_document 以项目为租户边界保存可复用文档；
-- 2) task_document 保存本次任务绑定的文档，library_document_id 非空表示来自文件库；
-- 3) content_hash 按项目去重，同一份文件再次选择“保存到文件库”时复用同一条记录与物理文件；
-- 4) 文档内容不进数据库，只保存平台存储目录中的 UUID 文件名。
-- =====================================================================================

CREATE TABLE `file_library_document` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`       BIGINT       NOT NULL,
    `source_task_id`   BIGINT       DEFAULT NULL,
    `filename`         VARCHAR(255) NOT NULL COMMENT '用户原始文件名，仅用于展示',
    `content_type`     VARCHAR(128) NOT NULL,
    `size_bytes`       BIGINT       NOT NULL,
    `content_hash`     CHAR(64)     NOT NULL COMMENT 'SHA-256，项目内相同内容只保存一份',
    `storage_path`     VARCHAR(512) NOT NULL COMMENT '平台侧绝对路径',
    `created_at`       DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_library_project_hash` (`project_id`, `content_hash`),
    KEY `idx_file_library_project_created` (`project_id`, `created_at`),
    CONSTRAINT `fk_file_library_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
    CONSTRAINT `fk_file_library_source_task`
        FOREIGN KEY (`source_task_id`) REFERENCES `task` (`id`) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目文件库文档';


CREATE TABLE `task_document` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`             BIGINT       NOT NULL,
    `library_document_id` BIGINT       DEFAULT NULL COMMENT '非空表示从文件库复用',
    `filename`            VARCHAR(255) NOT NULL,
    `content_type`        VARCHAR(128) NOT NULL,
    `size_bytes`          BIGINT       NOT NULL,
    `storage_path`        VARCHAR(512) NOT NULL,
    `created_at`          DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_task_document_task` (`task_id`, `id`),
    KEY `idx_task_document_library` (`library_document_id`),
    CONSTRAINT `fk_task_document_task`
        FOREIGN KEY (`task_id`) REFERENCES `task` (`id`),
    CONSTRAINT `fk_task_document_library`
        FOREIGN KEY (`library_document_id`) REFERENCES `file_library_document` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '任务文档';
