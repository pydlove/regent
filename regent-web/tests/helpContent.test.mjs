import assert from 'node:assert/strict'
import test from 'node:test'

const { HELP_TOPICS, filterHelpTopics } = await import('../src/content/help.ts')

test('按问题标题搜索常见问题', () => {
  const result = filterHelpTopics(HELP_TOPICS, '任务状态分别代表什么')

  assert.deepEqual(
    result.map((topic) => topic.id),
    ['task-statuses'],
  )
})

test('搜索范围包含关键词、步骤和分类', () => {
  assert.ok(filterHelpTopics(HELP_TOPICS, 'JSONL').some((topic) => topic.id === 'run-record'))
  assert.ok(filterHelpTopics(HELP_TOPICS, '验收标准').some((topic) => topic.id === 'write-good-instruction'))
  assert.ok(filterHelpTopics(HELP_TOPICS, '账户与安全').some((topic) => topic.id === 'sensitive-data'))
})

test('等级搜索不区分大小写并忽略首尾空格', () => {
  const result = filterHelpTopics(HELP_TOPICS, '  l2  ')

  assert.ok(result.some((topic) => topic.id === 'decision-levels'))
})

test('分类与关键词同时生效', () => {
  const result = filterHelpTopics(HELP_TOPICS, '环境', 'configuration')

  assert.deepEqual(
    result.map((topic) => topic.id),
    ['environment-entries', 'deployment-setup'],
  )
})

test('测试用例说明可按上传变量、绑定与失败状态检索', () => {
  assert.ok(
    filterHelpTopics(HELP_TOPICS, 'REGENT_TEST_FILE').some(
      (topic) => topic.id === 'test-case-create',
    ),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '绑定', 'testing').some(
      (topic) => topic.id === 'test-case-binding',
    ),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, 'TIMEOUT').some(
      (topic) => topic.id === 'test-case-results',
    ),
  )
})

test('语音输入说明可按麦克风与 HTTPS 检索', () => {
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '麦克风').some((topic) => topic.id === 'voice-input'),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, 'HTTPS').some((topic) => topic.id === 'voice-input'),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, 'Whisper').some((topic) => topic.id === 'voice-input'),
  )
})

test('CC Switch 下载说明可通过官网、快捷下载和供应商检索', () => {
  assert.ok(
    filterHelpTopics(HELP_TOPICS, 'CC Switch').some((topic) => topic.id === 'cc-switch-download'),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '快捷下载').some((topic) => topic.id === 'cc-switch-download'),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '供应商').some((topic) => topic.id === 'cc-switch-download'),
  )
})

test('分支执行说明可按 Git 与隔离分支检索', () => {
  assert.ok(
    filterHelpTopics(HELP_TOPICS, 'Git').some((topic) => topic.id === 'task-branch'),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, 'feature/').some((topic) => topic.id === 'task-branch'),
  )
})

test('任务草稿说明可按自动保存与恢复检索', () => {
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '自动保存').some((topic) => topic.id === 'task-draft'),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '恢复').some((topic) => topic.id === 'task-draft'),
  )
})

test('后续任务说明可按同一会话与继续创建检索', () => {
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '继续创建').some((topic) => topic.id === 'task-continuation'),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '同一会话').some((topic) => topic.id === 'task-continuation'),
  )
})

test('排队恢复说明可按重启、等待设备和执行位检索', () => {
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '重启').some((topic) => topic.id === 'queue-recovery'),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '等待设备').some((topic) => topic.id === 'queue-recovery'),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '执行位').some((topic) => topic.id === 'queue-recovery'),
  )
})

test('客户端更新说明可按 Runtime、回滚和发布码检索', () => {
  assert.ok(
    filterHelpTopics(HELP_TOPICS, 'Runtime').some((topic) => topic.id === 'runtime-update'),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '回滚').some((topic) => topic.id === 'runtime-update'),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, 'R100B014').some((topic) => topic.id === 'runtime-update'),
  )
})

test('部署失败说明可按健康检查和阶段检索', () => {
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '健康检查').some((topic) => topic.id === 'deployment-failure'),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '部署失败').some((topic) => topic.id === 'deployment-failure'),
  )
})

test('逐 Diff 审阅说明可按状态和单项操作检索', () => {
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '通过本项').some((topic) => topic.id === 'review-per-diff'),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '待决策').some((topic) => topic.id === 'review-per-diff'),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '本项要求修改').some((topic) => topic.id === 'review-per-diff'),
  )
})

test('Git 提交规范和记录说明可分别检索', () => {
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '102008').some((topic) => topic.id === 'git-commit-flow'),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '提交规范').some((topic) => topic.id === 'git-commit-flow'),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, 'Commit SHA').some(
      (topic) => topic.id === 'git-commit-records',
    ),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '提交失败').some(
      (topic) => topic.id === 'git-commit-records',
    ),
  )
})

test('打开项目代码说明可按编辑器和定位行号检索', () => {
  assert.ok(
    filterHelpTopics(HELP_TOPICS, 'Cursor').some((topic) => topic.id === 'open-project-code'),
  )
  assert.ok(
    filterHelpTopics(HELP_TOPICS, '定位行号').some((topic) => topic.id === 'open-project-code'),
  )
})

test('空搜索返回当前分类的全部条目', () => {
  const result = filterHelpTopics(HELP_TOPICS, '', 'tasks')
  const expected = HELP_TOPICS.filter((topic) => topic.category === 'tasks')

  assert.equal(result.length, expected.length)
})

test('FAQ 条目 ID 唯一，便于稳定展开与内容维护', () => {
  const ids = HELP_TOPICS.map((topic) => topic.id)

  assert.equal(new Set(ids).size, ids.length)
})
