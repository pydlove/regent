-- 基于已有任务创建后续任务。
--
-- 子任务会复制来源任务的 Codex 会话与分支，保证两条任务在同一条会话上下文里继续；
-- continued_from_task_id 只保存来源关系，不把两个聚合对象绑在一起。

ALTER TABLE `task`
    ADD COLUMN `continued_from_task_id` BIGINT DEFAULT NULL
        COMMENT '来源任务；非空表示基于该任务的 Codex 会话继续开发' AFTER `codex_thread_id`,
    ADD KEY `idx_task_continued_from` (`continued_from_task_id`),
    ADD CONSTRAINT `fk_task_continued_from`
        FOREIGN KEY (`continued_from_task_id`) REFERENCES `task` (`id`) ON DELETE SET NULL;
