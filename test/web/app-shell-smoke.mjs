#!/usr/bin/env node

const baseUrl = (process.env.WEB_BASE_URL || 'http://127.0.0.1:12580').replace(/\/$/, '')
const routes = [
  { path: '/', title: '工作台入口' },
  { path: '/tasks', title: '任务中心' },
  { path: '/test-cases', title: '测试用例' },
  { path: '/help', title: '使用说明' },
]

let failed = false

for (const route of routes) {
  try {
    const response = await fetch(`${baseUrl}${route.path}`, {
      signal: AbortSignal.timeout(10_000),
      headers: { Accept: 'text/html' },
    })
    const html = await response.text()
    const appShell = html.includes('<div id="app"></div>')
    const title = html.includes('<title>Regent AI 任务执行平台</title>')

    if (!response.ok || !appShell || !title) {
      failed = true
      console.error(
        `FAIL ${route.title} ${route.path}：HTTP ${response.status}，appShell=${appShell}，title=${title}`,
      )
      continue
    }
    console.log(`PASS ${route.title} ${route.path}`)
  } catch (error) {
    failed = true
    console.error(`FAIL ${route.title} ${route.path}：${error.message}`)
  }
}

if (failed) process.exit(1)
console.log(`Web 页面外壳冒烟通过：${baseUrl}`)
