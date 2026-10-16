-- =====================================================================================
-- V5：缺陷单补齐来源与不修复理由
--
-- V1 里 bug 表使用 HIGH/MEDIUM/LOW 与 VERIFIED/WONT_FIX，前端和领域词汇实际采用
-- BLOCKER/MAJOR/MINOR/TRIVIAL 与 CLOSED/WONTFIX。列本来就是 VARCHAR，这里只规范值并更新注释，
-- 不允许再回到两套枚举并存的状态。
--
-- source 用显式列而不是从 source_task_id 猜：人工录入与执行器发现是两条不同的产品入口，
-- 列表和审计都要能直接回答它是怎么来的。
-- =====================================================================================

ALTER TABLE `bug`
    ADD COLUMN `source` VARCHAR(16) NOT NULL DEFAULT 'AI_DISCOVERED'
        COMMENT 'MANUAL/AI_DISCOVERED' AFTER `fix_task_id`,
    ADD COLUMN `wont_fix_reason` TEXT DEFAULT NULL
        COMMENT '判为不修复时留下的理由' AFTER `detail`,
    MODIFY COLUMN `severity` VARCHAR(16) NOT NULL
        COMMENT 'BLOCKER/MAJOR/MINOR/TRIVIAL',
    MODIFY COLUMN `status` VARCHAR(16) NOT NULL
        COMMENT 'OPEN/FIXING/FIXED/CLOSED/WONTFIX';

UPDATE `bug`
SET `severity` = CASE `severity`
    WHEN 'HIGH' THEN 'MAJOR'
    WHEN 'MEDIUM' THEN 'MINOR'
    WHEN 'LOW' THEN 'TRIVIAL'
    ELSE `severity`
END,
    `status` = CASE `status`
        WHEN 'VERIFIED' THEN 'CLOSED'
        WHEN 'WONT_FIX' THEN 'WONTFIX'
        ELSE `status`
    END,
    `source` = CASE
        WHEN `source_task_id` IS NULL THEN 'MANUAL'
        ELSE 'AI_DISCOVERED'
    END;

-- 回填完成后取消默认值：来源是领域不变量，新写入必须由应用显式给出，不能靠数据库猜。
ALTER TABLE `bug`
    MODIFY COLUMN `source` VARCHAR(16) NOT NULL
        COMMENT 'MANUAL/AI_DISCOVERED';
