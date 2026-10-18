#!/usr/bin/env node

const webBaseUrl = (process.env.WEB_BASE_URL || 'http://127.0.0.1:12580').replace(/\/$/, '')
const contractUrl = `${webBaseUrl}/api/agent/contract`

try {
  const response = await fetch(contractUrl, {
    signal: AbortSignal.timeout(10_000),
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  const contract = await response.json()
  if (contract.protocol !== 'regent-agent/1') {
    throw new Error(`协议版本不正确：${contract.protocol ?? '缺失'}`)
  }

  console.log(`PASS 前端 API 代理 ${contractUrl}`)
  console.log(`协议版本：${contract.protocol}`)
} catch (error) {
  console.error(`FAIL 前端 API 代理 ${contractUrl}：${error.message}`)
  process.exit(1)
}
