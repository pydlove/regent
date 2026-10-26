import assert from 'node:assert/strict'
import test from 'node:test'

import { resolveApiResourceUrl } from '../src/api/http.ts'

test('子路径部署时头像资源地址保留应用前缀', () => {
  assert.equal(
    resolveApiResourceUrl('/api/profile/avatar?v=123', '/regent'),
    '/regent/api/profile/avatar?v=123',
  )
})

test('根路径部署时不重复拼接 API 前缀', () => {
  assert.equal(
    resolveApiResourceUrl('/api/profile/avatar?v=123', ''),
    '/api/profile/avatar?v=123',
  )
})

test('绝对地址和本地图片地址保持原样', () => {
  assert.equal(
    resolveApiResourceUrl('https://cdn.example.com/avatar.png', '/regent'),
    'https://cdn.example.com/avatar.png',
  )
  assert.equal(resolveApiResourceUrl('blob:local-avatar', '/regent'), 'blob:local-avatar')
  assert.equal(resolveApiResourceUrl(null, '/regent'), null)
})
