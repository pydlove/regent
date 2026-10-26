-- 同一执行记录允许重新生成审阅快照。旧快照保留并置为 EXPIRED，新快照以更大 ID 成为当前版本。
ALTER TABLE `review_snapshot`
    DROP INDEX `uk_review_snapshot_run`,
    ADD KEY `idx_review_snapshot_run` (`task_run_id`, `id`);
