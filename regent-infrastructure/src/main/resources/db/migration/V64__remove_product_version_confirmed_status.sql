-- 移除冗余的版本确认步骤；已确认版本回到规划中，关闭版本仍是唯一终止状态。
UPDATE `product_version`
SET `status` = 'PLANNING',
    `updated_at` = UTC_TIMESTAMP(3)
WHERE `status` = 'CONFIRMED';
