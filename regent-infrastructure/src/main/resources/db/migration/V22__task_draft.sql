-- 当前用户的任务录入草稿。草稿独立于 task，避免未写完的内容进入待执行队列。
CREATE TABLE `task_draft` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT      NOT NULL,
    `project_id`  BIGINT      DEFAULT NULL,
    `prompt`      MEDIUMTEXT  NOT NULL,
    `priority`    INT         NOT NULL DEFAULT 0,
    `plugin_ids`  JSON        NOT NULL COMMENT '按选择顺序保存的插件 ID 数组',
    `created_at`  DATETIME(3) NOT NULL,
    `updated_at`  DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_draft_user` (`user_id`),
    CONSTRAINT `fk_task_draft_user`
        FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`),
    CONSTRAINT `fk_task_draft_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '任务录入草稿';
