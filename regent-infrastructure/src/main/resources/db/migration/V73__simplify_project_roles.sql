-- 精简项目角色：发布评审和生产发布改由项目负责人执行，历史发布负责人转为项目负责人。
UPDATE `workflow_template_stage`
SET `approver_role` = 'OWNER'
WHERE `stage_code` IN ('RELEASE_REVIEW', 'PRODUCTION_RELEASE')
  AND `approver_role` = 'RELEASE_MANAGER';

INSERT IGNORE INTO `project_member` (`project_id`, `user_id`, `role_code`, `created_at`)
SELECT `project_id`, `user_id`, 'OWNER', `created_at`
FROM `project_member`
WHERE `role_code` = 'RELEASE_MANAGER';

DELETE FROM `project_member`
WHERE `role_code` = 'RELEASE_MANAGER';
