-- 需求编号全局唯一、全局递增；展示时至少补齐 6 位，超过 999999 后自然扩位。
ALTER TABLE `requirement`
    ADD COLUMN `requirement_no` BIGINT DEFAULT NULL AFTER `id`;

UPDATE `requirement` r
JOIN (
    SELECT `id`,
           ROW_NUMBER() OVER (ORDER BY `created_at`, `id`) AS `requirement_no`
    FROM `requirement`
) numbered ON numbered.`id` = r.`id`
SET r.`requirement_no` = numbered.`requirement_no`;

ALTER TABLE `requirement`
    MODIFY COLUMN `requirement_no` BIGINT NOT NULL,
    ADD UNIQUE KEY `uk_requirement_no` (`requirement_no`);

-- AUTO_INCREMENT 表负责并发安全地发放全局编号；记录只增不改，业务删除不回退编号。
CREATE TABLE `requirement_number_sequence` (
    `requirement_no` BIGINT NOT NULL AUTO_INCREMENT,
    PRIMARY KEY (`requirement_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '全局需求编号序列';

-- 显式写入历史编号，MySQL 会把下一个 AUTO_INCREMENT 自动推进到最大值之后。
INSERT INTO `requirement_number_sequence` (`requirement_no`)
SELECT `requirement_no` FROM `requirement` ORDER BY `requirement_no`;
