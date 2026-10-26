-- 旧流程中的 IN_REVIEW 需求没有评审批次归属，版本页无法继续处理。
-- 仅重置这类孤立状态，正在新评审批次中的需求保持不变。
UPDATE `requirement` r
LEFT JOIN (
    SELECT DISTINCT jt.`requirement_id`
    FROM `requirement_review_round` rr
    JOIN JSON_TABLE(
        rr.`items_json`,
        '$[*]' COLUMNS (`requirement_id` BIGINT PATH '$.requirementId')
    ) jt
) reviewed ON reviewed.`requirement_id` = r.`id`
SET r.`pool_status` = 'POOL',
    r.`review_comment` = NULL,
    r.`reviewed_by` = NULL,
    r.`reviewed_at` = NULL,
    r.`updated_at` = UTC_TIMESTAMP(3)
WHERE r.`pool_status` = 'IN_REVIEW'
  AND r.`deleted` = 0
  AND reviewed.`requirement_id` IS NULL;
