import assert from 'node:assert/strict'
import test from 'node:test'

import {
  filterPlugins,
  matchesPlugin,
  pluginTagOptions,
} from '../src/utils/pluginCatalog.ts'

const plugins = [
  {
    name: 'Kafka 读取组件',
    description: '复用消费者配置',
    tags: ['Kafka', 'Java'],
    filename: 'KafkaReader.java',
    enabled: true,
    currentRevision: 3,
  },
  {
    name: '数据库巡检',
    description: '检查连接池与慢查询',
    tags: ['MySQL', '运维'],
    filename: 'db-check.sql',
    enabled: false,
    currentRevision: 1,
  },
]

test('搜索名称、说明、文件名、版本与标签', () => {
  assert.equal(matchesPlugin(plugins[0], 'kafka'), true)
  assert.equal(matchesPlugin(plugins[0], '消费者'), true)
  assert.equal(matchesPlugin(plugins[0], 'reader.java'), true)
  assert.equal(matchesPlugin(plugins[0], 'v3'), true)
  assert.equal(matchesPlugin(plugins[1], '运维'), true)
  assert.equal(matchesPlugin(plugins[1], '不存在'), false)
})

test('状态、标签与关键词同时生效', () => {
  assert.deepEqual(
    filterPlugins(plugins, { status: 'enabled', tag: 'java', keyword: '组件' }).map(
      (plugin) => plugin.name,
    ),
    ['Kafka 读取组件'],
  )
  assert.deepEqual(
    filterPlugins(plugins, { status: 'disabled' }).map((plugin) => plugin.name),
    ['数据库巡检'],
  )
})

test('标签按使用频率去重并限制数量', () => {
  const options = pluginTagOptions([
    ...plugins,
    { ...plugins[1], name: 'MySQL 诊断', tags: ['MySQL', 'kafka'] },
  ])

  assert.deepEqual(options.slice(0, 2), [
    { value: 'Kafka', count: 2 },
    { value: 'MySQL', count: 2 },
  ])
  assert.equal(options.length, 4)
  assert.equal(options.find((tag) => tag.value === 'Java')?.count, 1)
  assert.equal(options.find((tag) => tag.value === '运维')?.count, 1)
})
