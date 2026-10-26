-- 项目成员与角色。角色属于项目，不写在 app_user 上。
-- 现有一个项目只有一个 owner；先为历史项目补 OWNER 成员。

CREATE TABLE `project_member` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `project_id`  BIGINT      NOT NULL,
    `user_id`     BIGINT      NOT NULL,
    `role_code`   VARCHAR(32) NOT NULL,
    `created_at`  DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_member_role` (`project_id`, `user_id`, `role_code`),
    KEY `idx_project_member_user` (`user_id`, `project_id`),
    CONSTRAINT `fk_project_member_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_project_member_user`
        FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目成员及角色';

INSERT INTO `project_member` (`project_id`, `user_id`, `role_code`, `created_at`)
SELECT `id`, `owner_id`, 'OWNER', UTC_TIMESTAMP(3)
FROM `project`
WHERE `owner_id` IS NOT NULL;

ALTER TABLE `delivery_gate`
    ADD COLUMN `role_code` VARCHAR(32) DEFAULT NULL
        COMMENT '本次审批要求的项目角色' AFTER `decision`;

UPDATE `delivery_gate`
SET `role_code` = 'OWNER'
WHERE `role_code` IS NULL;
