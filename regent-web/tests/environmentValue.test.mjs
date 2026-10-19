import assert from 'node:assert/strict'
import test from 'node:test'

const {
  hasInlineUrlCredentials,
  isHttpUrl,
  isMaskedEnvironmentValue,
} = await import('../src/utils/environmentValue.ts')

test('只把 http 和 https 地址识别为可点击链接', () => {
  assert.equal(isHttpUrl('http://172.16.53.201:3390/'), true)
  assert.equal(isHttpUrl('https://example.com/path?q=1'), true)
  assert.equal(isHttpUrl('mysql://127.0.0.1:3306/app'), false)
  assert.equal(isHttpUrl('javascript:alert(1)'), false)
  assert.equal(isHttpUrl('not a url'), false)
})

test('密码标记和 URL 内嵌凭据都需要脱敏', () => {
  assert.equal(isMaskedEnvironmentValue({ value: 'http://example.com', secret: false }), false)
  assert.equal(isMaskedEnvironmentValue({ value: 'http://example.com', secret: true }), true)
  assert.equal(
    isMaskedEnvironmentValue({ value: 'https://user:pass@example.com', secret: false }),
    true,
  )
  assert.equal(
    isMaskedEnvironmentValue({
      value: 'jdbc:mysql://root:pass@127.0.0.1:3306/app',
      secret: false,
    }),
    true,
  )
  assert.equal(hasInlineUrlCredentials('https://example.com'), false)
})
