-- 公司提交规范：括号内必须是 6 位数字 Bug 编号。
-- 只升级仍使用旧默认模板的行，企业已经自定义的模板保持不变。
UPDATE `git_commit_setting`
SET `default_scope` = NULL,
    `subject_template` = '{type}({ticket}): {title}',
    `validation_pattern` = '(?s)^(feat|fix|refactor|docs|test|build|chore|perf|ci|style)(\\(\\d{6}\\)): [^\\r\\n]+(?:\\R\\R[\\s\\S]+)?$',
    `validation_hint` = '格式：type(6位Bug编号): 中文说明，例如 feat(102008): 优化告警记录处理逻辑并添加组件配置',
    `updated_at` = UTC_TIMESTAMP(3)
WHERE `id` = 1
  AND `subject_template` = '{type}({scope}): {title}';
