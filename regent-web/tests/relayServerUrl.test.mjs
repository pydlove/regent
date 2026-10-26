import assert from 'node:assert/strict'
import test from 'node:test'

import {
  defaultRelayServerUrl,
  inspectRelayServerUrl,
  isRelayServerHost,
} from '../src/utils/relayServerUrl.ts'

test('服务器地址支持完整域名和省略协议的域名', () => {
  assert.deepEqual(inspectRelayServerUrl('https://regent.example.com', true), {
    valid: true,
    normalized: 'https://regent.example.com/',
    pagePath: '',
  })
  assert.deepEqual(inspectRelayServerUrl('regent.example.com', true), {
    valid: true,
    normalized: 'https://regent.example.com/',
    pagePath: '',
  })
})

test('域名子路径会保留，粘贴应用页面时回到对应站点根地址', () => {
  assert.deepEqual(inspectRelayServerUrl('ichuang.top/regent/', true), {
    valid: true,
    normalized: 'https://ichuang.top/regent/',
    pagePath: '',
  })
  assert.deepEqual(inspectRelayServerUrl('https://ichuang.top/regent/decisions', true), {
    valid: true,
    normalized: 'https://ichuang.top/regent/',
    pagePath: '/decisions',
  })
  assert.deepEqual(inspectRelayServerUrl('https://ichuang.top/regent/settings/relay', true), {
    valid: true,
    normalized: 'https://ichuang.top/regent/',
    pagePath: '/settings/relay',
  })
})

test('裸 IP 默认使用当前 HTTP 端口，域名默认使用 HTTPS', () => {
  assert.equal(
    inspectRelayServerUrl('101.35.228.195:18787', true).normalized,
    'http://101.35.228.195:18787/',
  )
  assert.equal(defaultRelayServerUrl('101.35.228.195'), 'http://101.35.228.195:18787')
  assert.equal(defaultRelayServerUrl('regent.example.com'), 'https://regent.example.com/')
})

test('地址校验拒绝登录信息、查询参数、非法协议和无效主机名', () => {
  for (const value of [
    'ftp://regent.example.com',
    'https://user:pass@regent.example.com',
    'https://regent.example.com/?token=secret',
    'https://regent.example.com/#fragment',
    'https://bad_host.example.com',
    'https://999.999.999.999',
    'https://regent.example.com has-space',
  ]) {
    assert.equal(inspectRelayServerUrl(value, true).valid, false, value)
  }
})

test('SSH 服务器字段同时接受域名与 IP', () => {
  assert.equal(isRelayServerHost('regent.example.com'), true)
  assert.equal(isRelayServerHost('101.35.228.195'), true)
  assert.equal(isRelayServerHost('-regent.example.com'), false)
  assert.equal(isRelayServerHost('regent..example.com'), false)
  assert.equal(isRelayServerHost('https://regent.example.com'), false)
  assert.equal(isRelayServerHost('regent.example.com:22'), false)
})
