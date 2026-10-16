-- -------------------------------------------------------------------------------------
-- 图片附件 —— 有些任务光靠文字说不清（界面长什么样、报错截图、设计稿）
--
-- 只存元数据与平台侧路径，图片本体落在 regent.attachments.dir 下：
--   · 路径不进仓库、不进任务工作区 —— 工作区是项目的代码，往里面丢平台的临时文件会污染 git 状态；
--   · 对外不暴露 storage_path，看图走 GET /api/attachments/{id}/content 一个出口。
--
-- 没有 deleted 列：附件是不可变的事实，生命周期跟着任务走（删任务时才删文件与行），
-- 现在没有「单独删一张图」的用例，不加软删标记。
-- -------------------------------------------------------------------------------------
CREATE TABLE `task_attachment` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`      BIGINT       NOT NULL,
    `filename`     VARCHAR(255) NOT NULL COMMENT '用户的原始文件名，仅用于展示',
    `content_type` VARCHAR(128) NOT NULL COMMENT 'image/png 等；非白名单内的图片类型一律拒收',
    `size_bytes`   BIGINT       NOT NULL,
    `storage_path` VARCHAR(512) NOT NULL COMMENT '平台侧绝对路径，派发时交给 codex 的 -i',
    `created_at`   DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_attachment_task` (`task_id`),
    CONSTRAINT `fk_attachment_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '任务图片附件';
