import assert from 'node:assert/strict'
import test from 'node:test'

const { ApiError } = await import('../src/api/http.ts')
const { shouldRetryAuth } = await import('../src/utils/authRecovery.ts')

test('后端重启和网络异常时保留会话并重试', () => {
  assert.equal(shouldRetryAuth(new ApiError(0, 'TIMEOUT', '请求超时')), true)
  assert.equal(shouldRetryAuth(new ApiError(502, 'RELAY_BAD_RESPONSE', '中继失败')), true)
  assert.equal(shouldRetryAuth(new ApiError(503, 'RELAY_OFFLINE', '中继离线')), true)
  assert.equal(shouldRetryAuth(new TypeError('Failed to fetch')), true)
})

test('只有明确的认证失败和普通业务错误才停止重试', () => {
  assert.equal(shouldRetryAuth(new ApiError(401, 'UNAUTHORIZED', '登录已失效')), false)
  assert.equal(shouldRetryAuth(new ApiError(400, 'INVALID_ARGUMENT', '参数错误')), false)
})
