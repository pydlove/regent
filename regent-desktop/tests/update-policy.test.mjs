import assert from 'node:assert/strict'
import test from 'node:test'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const {
  clientUpdateReleaseCode,
  shouldPromptForClientUpdate,
} = require('../update-policy.js')

test('纯网页发布不触发桌面客户端升级', () => {
  const manifest = {
    version: 'Regent_V1.0.2_R100B012',
    releaseCode: 'R100B011',
    webOnly: true,
    runtime: null,
  }

  assert.equal(clientUpdateReleaseCode(manifest), '')
  assert.equal(shouldPromptForClientUpdate(manifest, 'R100B011'), false)
})

test('只有 Runtime 发布码变化时才提示客户端升级', () => {
  const manifest = {
    version: 'Regent_V1.0.2_R100B013',
    releaseCode: 'R100B013',
    webOnly: false,
    runtime: {
      releaseCode: 'R100B013',
    },
  }

  assert.equal(clientUpdateReleaseCode(manifest), 'R100B013')
  assert.equal(shouldPromptForClientUpdate(manifest, 'R100B012'), true)
  assert.equal(shouldPromptForClientUpdate(manifest, 'R100B013'), false)
  assert.equal(shouldPromptForClientUpdate(manifest, 'R100B012', 'R100B013'), false)
})

test('缺少 Runtime 的旧清单仍按客户端发布码兼容判断', () => {
  const manifest = {
    version: 'Regent_V1.0.2_R100B012',
    releaseCode: 'R100B012',
    runtime: null,
  }

  assert.equal(clientUpdateReleaseCode(manifest), 'R100B012')
  assert.equal(shouldPromptForClientUpdate(manifest, 'R100B011'), true)
})

test('启动失败并回滚的 Runtime 不会再次弹出更新', () => {
  const manifest = {
    version: 'Regent_V1.0.3_R100B001',
    releaseCode: 'R100B001',
    runtime: {
      releaseCode: 'R100B001',
    },
  }

  assert.equal(
    shouldPromptForClientUpdate(manifest, 'R100B011', null, 'R100B001'),
    false,
  )
  assert.equal(
    shouldPromptForClientUpdate(manifest, 'R100B011', null, 'R100B002'),
    true,
  )
})

test('跳过的版本不再提示，新版本仍会提示', () => {
  const manifest = {
    version: 'Regent_V1.0.3_R100B020',
    releaseCode: 'R100B020',
    runtime: {
      releaseCode: 'R100B020',
    },
  }

  assert.equal(
    shouldPromptForClientUpdate(manifest, 'R100B011', null, null, 'R100B020'),
    false,
  )
  assert.equal(
    shouldPromptForClientUpdate(manifest, 'R100B011', null, null, 'R100B019'),
    true,
  )
})
