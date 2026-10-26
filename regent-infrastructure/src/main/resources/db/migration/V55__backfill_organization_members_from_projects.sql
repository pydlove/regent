-- 组织层上线前已经存在的项目成员，补成对应组织的普通成员。
-- 已有任一组织角色的人不重复插入。
INSERT INTO `organization_member`
    (`organization_id`, `user_id`, `role_code`, `status`, `joined_at`, `created_at`)
SELECT DISTINCT
    p.`organization_id`,
    pm.`user_id`,
    'ORG_MEMBER',
    'ACTIVE',
    pm.`created_at`,
    UTC_TIMESTAMP(3)
FROM `project_member` pm
JOIN `project` p ON p.`id` = pm.`project_id`
WHERE p.`organization_id` IS NOT NULL
  AND p.`deleted` = 0
  AND NOT EXISTS (
      SELECT 1
      FROM `organization_member` om
      WHERE om.`organization_id` = p.`organization_id`
        AND om.`user_id` = pm.`user_id`
        AND om.`status` = 'ACTIVE'
  );
