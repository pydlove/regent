import assert from 'node:assert/strict'
import fs from 'node:fs'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const {
  DeviceAgent,
  executable,
  loginShellCodex,
} = require('../device-agent.js')

function script(file, content) {
  fs.mkdirSync(path.dirname(file), { recursive: true })
  fs.writeFileSync(file, content, { mode: 0o755 })
  fs.chmodSync(file, 0o755)
  return file
}

test('只接受当前设备上真实可执行的 Codex 文件', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-agent-detect-'))
  try {
    const codex = script(path.join(directory, 'bin/codex'), '#!/bin/sh\nexit 0\n')
    const plain = path.join(directory, 'not-executable')
    fs.writeFileSync(plain, '#!/bin/sh\n')
    assert.equal(executable(codex), codex)
    assert.equal(executable(plain), null)
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('从登录 shell 的实际 PATH 解析自定义安装位置', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-agent-shell-'))
  try {
    const codex = script(path.join(directory, 'custom/bin/codex'), '#!/bin/sh\nexit 0\n')
    const shell = script(
      path.join(directory, 'fake-shell'),
      `#!/bin/sh\nprintf '\\n__REGENT_CODEX_PATH__=%s\\n' '${codex}'\n`,
    )
    assert.equal(await loginShellCodex(shell), codex)
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('本机代理只接受本机页面或已配对服务器来源', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-agent-origin-'))
  try {
    const agent = new DeviceAgent({
      stateFile: path.join(directory, 'state.json'),
      serverUrl: 'https://regent.example.com',
    })
    assert.equal(agent.allowOrigin('http://127.0.0.1:12580'), true)
    assert.equal(agent.allowOrigin('https://regent.example.com'), true)
    assert.equal(agent.allowOrigin('https://evil.example.com'), false)
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('浏览器用有效 Bearer 会话完成设备代理配对', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-agent-pair-'))
  const server = http.createServer((request, response) => {
    assert.equal(request.url, '/api/auth/me')
    assert.equal(request.headers.authorization, 'Bearer valid-device-token')
    response.writeHead(200, { 'Content-Type': 'application/json' })
    response.end('{"id":1}')
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  try {
    const { port } = server.address()
    const agent = new DeviceAgent({
      stateFile: path.join(directory, 'state.json'),
      localPort: 12699,
    })
    const status = await agent.pair(`http://127.0.0.1:${port}`, 'valid-device-token')
    assert.equal(status.paired, true)
    assert.equal(status.serverUrl, `http://127.0.0.1:${port}`)
    assert.equal(agent.allowOrigin(`http://127.0.0.1:${port}`), true)
  } finally {
    await new Promise((resolve) => server.close(resolve))
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('设备代理在本机启动 Codex 并读取结构化结果', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-agent-exec-'))
  const workdir = path.join(directory, 'project')
  fs.mkdirSync(workdir)
  const fakeCodex = script(path.join(directory, 'bin/codex'), `#!/bin/sh
output=""
while [ "$#" -gt 0 ]; do
  if [ "$1" = "-o" ]; then
    output="$2"
    shift 2
  else
    shift
  fi
done
cat >/dev/null
printf '%s\\n' '{"type":"thread.started","thread_id":"thread-local"}'
printf '%s\\n' '{"status":"DONE","summary":"本机完成","changedFiles":["src/a.ts"],"bugs":[]}' > "$output"
`)
  const server = http.createServer((request, response) => {
    assert.equal(request.headers.authorization, 'Bearer device-token')
    response.writeHead(200, { 'Content-Type': 'application/json' })
    response.end(JSON.stringify({
      resultSchema: { type: 'object' },
      contract: '必须输出结构化 JSON',
    }))
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  try {
    const { port } = server.address()
    const agent = new DeviceAgent({
      stateFile: path.join(directory, 'state.json'),
      serverUrl: `http://127.0.0.1:${port}`,
      token: 'device-token',
      localPort: 12698,
    })
    const result = await agent.runCodex({
      executablePath: fakeCodex,
      work: {
        workdir,
        images: [],
        environment: [],
      },
      instruction: '完成本机任务',
      sessionId: null,
      onThread: () => {},
    })
    assert.equal(result.exitCode, 0)
    assert.equal(result.threadId, 'thread-local')
    assert.equal(result.report.status, 'DONE')
    assert.equal(result.report.summary, '本机完成')
  } finally {
    await new Promise((resolve) => server.close(resolve))
    fs.rmSync(directory, { recursive: true, force: true })
  }
})
