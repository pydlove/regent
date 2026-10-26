-- 精简项目角色：业务负责人不再作为可分配角色。
UPDATE `workflow_template_stage`
SET `approver_role` = 'OWNER'
WHERE `approver_role` = 'BUSINESS_OWNER';

DELETE FROM `project_member`
WHERE `role_code` = 'BUSINESS_OWNER';
