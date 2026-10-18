-- -------------------------------------------------------------------------------------
-- 测试用例 —— 任务完成后自动验证改动的可复用资产。
--
-- 用例正文直接存库而不是落运行目录：用例属于配置，要跟任务、执行记录一起备份和迁移；
-- 执行器会在本次运行自己的临时目录里还原脚本，跑完即删，不污染项目工作区。
-- -------------------------------------------------------------------------------------
CREATE TABLE `test_case` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`      BIGINT       NOT NULL,
    `name`            VARCHAR(128) NOT NULL,
    `test_type`       VARCHAR(16)  NOT NULL COMMENT 'WEB/API',
    `description`     VARCHAR(1024) DEFAULT NULL,
    `command_text`    TEXT         DEFAULT NULL COMMENT '在项目目录执行的 shell 命令；可用 REGENT_TEST_FILE',
    `script_filename` VARCHAR(255) DEFAULT NULL COMMENT '上传脚本的展示文件名',
    `script_content`  MEDIUMTEXT   DEFAULT NULL COMMENT 'UTF-8 文本；执行时还原到临时目录',
    `script_hash`     CHAR(64)     DEFAULT NULL COMMENT '脚本内容 SHA-256',
    `enabled`         TINYINT(1)   NOT NULL DEFAULT 1,
    `deleted`         TINYINT(1)   NOT NULL DEFAULT 0,
    `created_at`      DATETIME(3)  NOT NULL,
    `updated_at`      DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_test_case_project` (`project_id`, `deleted`, `enabled`),
    KEY `idx_test_case_name` (`project_id`, `name`),
    CONSTRAINT `fk_test_case_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Web/接口测试用例';

-- 任务绑定只保存 ID；执行时读取用例当前版本，并把命令与脚本快照写进 test_case_run。
CREATE TABLE `task_test_case` (
    `task_id`      BIGINT      NOT NULL,
    `test_case_id` BIGINT      NOT NULL,
    `bound_at`     DATETIME(3) NOT NULL,
    PRIMARY KEY (`task_id`, `test_case_id`),
    KEY `idx_task_test_case_case` (`test_case_id`),
    CONSTRAINT `fk_task_test_case_task`
        FOREIGN KEY (`task_id`) REFERENCES `task` (`id`),
    CONSTRAINT `fk_task_test_case_case`
        FOREIGN KEY (`test_case_id`) REFERENCES `test_case` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '任务与测试用例绑定';

-- 执行记录保存用例快照：即使用例之后改名、换命令或停用，历史结论仍能解释清楚。
CREATE TABLE `test_case_run` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`         BIGINT       NOT NULL,
    `test_case_id`    BIGINT       NOT NULL,
    `test_case_name`  VARCHAR(128) NOT NULL,
    `test_type`       VARCHAR(16)  NOT NULL,
    `command_text`    TEXT         DEFAULT NULL,
    `script_filename` VARCHAR(255) DEFAULT NULL,
    `script_content`  MEDIUMTEXT   DEFAULT NULL,
    `script_hash`     CHAR(64)     DEFAULT NULL,
    `status`          VARCHAR(16)  NOT NULL COMMENT 'QUEUED/RUNNING/SUCCESS/FAILED/TIMEOUT/INTERRUPTED/SKIPPED',
    `exit_code`       INT          DEFAULT NULL,
    `summary`         VARCHAR(1024) DEFAULT NULL,
    `output`          MEDIUMTEXT   DEFAULT NULL COMMENT 'stdout 与 stderr 合并后的原文，截断到配置上限',
    `queued_at`       DATETIME(3)  NOT NULL,
    `started_at`      DATETIME(3)  DEFAULT NULL,
    `finished_at`     DATETIME(3)  DEFAULT NULL,
    `created_at`      DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_test_run_task` (`task_id`, `id`),
    KEY `idx_test_run_case` (`test_case_id`, `id`),
    KEY `idx_test_run_status` (`status`),
    CONSTRAINT `fk_test_run_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`),
    CONSTRAINT `fk_test_run_case` FOREIGN KEY (`test_case_id`) REFERENCES `test_case` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '测试用例执行记录';
