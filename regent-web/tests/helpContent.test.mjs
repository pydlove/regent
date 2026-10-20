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

test('空搜索返回当前分类的全部条目', () => {
  const result = filterHelpTopics(HELP_TOPICS, '', 'tasks')
  const expected = HELP_TOPICS.filter((topic) => topic.category === 'tasks')

  assert.equal(result.length, expected.length)
})

test('FAQ 条目 ID 唯一，便于稳定展开与内容维护', () => {
  const ids = HELP_TOPICS.map((topic) => topic.id)

  assert.equal(new Set(ids).size, ids.length)
})
