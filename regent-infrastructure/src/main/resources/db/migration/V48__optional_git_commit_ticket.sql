-- 提交说明由任务上下文自动生成：有关联 Bug 时带 6 位编号，没有关联时省略括号编号。
UPDATE `git_commit_setting`
SET `subject_template` = '{type}({ticket}): {title}',
    `validation_pattern` = '(?s)^(feat|fix|refactor|docs|test|build|chore|perf|ci|style)(\\(\\d{6}\\))?: [^\\r\\n]+(?:\\R\\R[\\s\\S]+)?$',
    `validation_hint` = '格式：type(6位Bug编号): 中文说明；未关联 Bug 时生成 type: 中文说明。例如 feat(102008): 优化告警记录处理逻辑，或 feat: 完善变更审阅',
    `updated_at` = UTC_TIMESTAMP(3)
WHERE `id` = 1
  AND `subject_template` = '{type}({ticket}): {title}'
  AND `validation_pattern` = '(?s)^(feat|fix|refactor|docs|test|build|chore|perf|ci|style)(\\(\\d{6}\\)): [^\\r\\n]+(?:\\R\\R[\\s\\S]+)?$';
