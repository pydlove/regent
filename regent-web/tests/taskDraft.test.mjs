import assert from 'node:assert/strict'
import test from 'node:test'

import {
  normalizeTaskDraftFields,
  taskDraftAutosaveDelay,
  taskDraftHasContent,
} from '../src/utils/taskDraft.ts'

test('草稿字段规范化会去掉重复插件并修正非法优先级', () => {
  assert.deepEqual(
    normalizeTaskDraftFields({
      projectId: 3,
      prompt: '写任务',
      priority: Number.NaN,
      pluginIds: [8, 8, 9],
    }),
    { projectId: 3, prompt: '写任务', priority: 0, pluginIds: [8, 9] },
  )
})

test('空白表单不创建草稿，有正文或插件才需要保存', () => {
  const empty = { projectId: 3, prompt: '  \n ', priority: 0, pluginIds: [] }
  assert.equal(taskDraftHasContent(empty, false), false)
  assert.equal(taskDraftHasContent({ ...empty, prompt: '开始写' }, false), true)
  assert.equal(taskDraftHasContent({ ...empty, pluginIds: [8] }, false), true)
})

test('已有草稿即使被清空也继续保存，避免关闭后恢复旧内容', () => {
  const empty = { projectId: null, prompt: '', priority: 0, pluginIds: [] }
  assert.equal(taskDraftHasContent(empty, true), true)
})

test('首次保存尚未返回时清空输入也要补存空快照', () => {
  const empty = { projectId: null, prompt: '', priority: 0, pluginIds: [] }
  assert.equal(taskDraftHasContent(empty, false, true), true)
})

test('连续输入仍会在最长等待时间到达时触发自动保存', () => {
  assert.equal(taskDraftAutosaveDelay(1_000, 1_000), 600)
  assert.equal(taskDraftAutosaveDelay(1_000, 3_800), 200)
  assert.equal(taskDraftAutosaveDelay(1_000, 4_000), 0)
  assert.equal(taskDraftAutosaveDelay(1_000, 8_000), 0)
})
