import assert from 'node:assert/strict'
import test from 'node:test'

import { parseTaskIdQuery } from '../src/utils/taskNavigation.ts'

test('解析合法任务号与路由数组参数', () => {
  assert.equal(parseTaskIdQuery('126'), 126)
  assert.equal(parseTaskIdQuery(' 126 '), 126)
  assert.equal(parseTaskIdQuery(98), 98)
  assert.equal(parseTaskIdQuery(['55', '98']), 55)
})

test('拒绝空值、零、负数和带杂质的任务号', () => {
  assert.equal(parseTaskIdQuery(null), null)
  assert.equal(parseTaskIdQuery(undefined), null)
  assert.equal(parseTaskIdQuery(''), null)
  assert.equal(parseTaskIdQuery('0'), null)
  assert.equal(parseTaskIdQuery('-1'), null)
  assert.equal(parseTaskIdQuery('12a'), null)
  assert.equal(parseTaskIdQuery('1.5'), null)
})
