import assert from 'node:assert/strict'
import test from 'node:test'

const { HELP_TOPICS, filterHelpTopics } = await import('../src/content/help.ts')

test('按问题标题搜索常见问题', () => {
  const result = filterHelpTopics(HELP_TOPICS, '任务状态')

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
    ['environment-entries'],
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
