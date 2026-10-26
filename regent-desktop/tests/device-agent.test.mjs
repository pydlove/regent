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
  codexInDirectory,
  executable,
  loginShellCodex,
  resumeContextInvalid,
} = require('../device-agent.js')
const {
  DEFAULT_LANDING_PATH,
  DEFAULT_SERVER_URL,
  appendServerPath,
  normalizeServerUrl,
} = require('../server-url.js')

function script(file, content) {
  fs.mkdirSync(path.dirname(file), { recursive: true })
  fs.writeFileSync(file, content, { mode: 0o755 })
  fs.chmodSync(file, 0o755)
  return file
}

test('桌面客户端服务地址保留 HTTPS 子路径', () => {
  const serverUrl = normalizeServerUrl('https://ichuang.top/regent/')
  assert.equal(serverUrl, 'https://ichuang.top/regent')
  assert.equal(
    appendServerPath(serverUrl, '/api/release/latest'),
    'https://ichuang.top/regent/api/release/latest',
  )
  assert.equal(
    appendServerPath(serverUrl, '/downloads/Regent.dmg'),
    'https://ichuang.top/regent/downloads/Regent.dmg',
  )
  assert.equal(
    appendServerPath(serverUrl, 'https://cdn.example.com/Regent.dmg'),
    'https://cdn.example.com/Regent.dmg',
  )
})

test('桌面客户端内置地址不要求用户填写，并默认打开决策页', () => {
  assert.equal(DEFAULT_SERVER_URL, 'https://ichuang.top/regent')
  assert.equal(DEFAULT_LANDING_PATH, '/decisions')
  assert.equal(
    appendServerPath(DEFAULT_SERVER_URL, DEFAULT_LANDING_PATH),
    'https://ichuang.top/regent/decisions',
  )
  assert.equal(
    appendServerPath(DEFAULT_SERVER_URL, '/api/release/latest'),
    'https://ichuang.top/regent/api/release/latest',
  )
})

async function availablePort() {
  const server = http.createServer()
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  const { port } = server.address()
  await new Promise((resolve) => server.close(resolve))
  return port
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

test('识别 Codex 已不存在的续跑会话', () => {
  assert.equal(
    resumeContextInvalid(
      'Error: thread/resume failed: no rollout found for thread id 01a157f6 (code -32600)',
    ),
    true,
  )
  assert.equal(resumeContextInvalid('HTTP 503 服务暂不可用'), false)
})

test('Windows 能识别 npm 安装的 codex.cmd', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-agent-windows-'))
  try {
    const codex = script(path.join(directory, 'codex.cmd'), '@echo off\r\n')
    assert.equal(codexInDirectory(directory, 'win32'), codex)
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

test('未配对时公网页面仍可通过私有网络预检检测本机 Codex', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-agent-pna-'))
  const agent = new DeviceAgent({
    stateFile: path.join(directory, 'state.json'),
    localPort: await availablePort(),
  })
  const origin = 'http://101.35.228.195:18787'
  try {
    await agent.startLocalServer()
    const preflight = await fetch(`http://127.0.0.1:${agent.localPort}/status`, {
      method: 'OPTIONS',
      headers: {
        Origin: origin,
        'Access-Control-Request-Method': 'GET',
        'Access-Control-Request-Private-Network': 'true',
      },
    })
    assert.equal(preflight.status, 204)
    assert.equal(preflight.headers.get('access-control-allow-origin'), origin)
    assert.equal(preflight.headers.get('access-control-allow-private-network'), 'true')

    const status = await fetch(`http://127.0.0.1:${agent.localPort}/status`, {
      headers: { Origin: origin },
    })
    assert.equal(status.status, 200)
    assert.equal(status.headers.get('access-control-allow-origin'), origin)
    assert.equal((await status.json()).paired, false)
  } finally {
    agent.stopLocalServer()
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('桌面主进程等本机客户端不携带 Origin 也能访问设备代理', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-agent-local-client-'))
  const agent = new DeviceAgent({
    stateFile: path.join(directory, 'state.json'),
    localPort: await availablePort(),
  })
  try {
    await agent.startLocalServer()
    const response = await fetch(`http://127.0.0.1:${agent.localPort}/run-task`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ taskId: 0 }),
    })
    assert.equal(response.status, 400)
    assert.match((await response.json()).error, /任务号/)
  } finally {
    agent.stopLocalServer()
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('设备代理按配置限制本地并发执行数', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-agent-capacity-limit-'))
  try {
    const agent = new DeviceAgent({
      stateFile: path.join(directory, 'state.json'),
      maxConcurrentTasks: 1,
    })
    agent.activeTasks.add(7)
    assert.throws(
      () => agent.runTask({ taskId: 8, projectCode: 'regent' }),
      /最多同时执行 1 个任务/,
    )
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('浏览器用有效 Bearer 会话完成设备代理配对', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-agent-pair-'))
  let agent
  let receivePresence
  const presenceReceived = new Promise((resolve) => {
    receivePresence = resolve
  })
  const server = http.createServer((request, response) => {
    assert.equal(request.headers.authorization, 'Bearer valid-device-token')
    response.writeHead(200, { 'Content-Type': 'application/json' })
    if (request.url === '/regent/api/device-agent/presence') {
      let body = ''
      request.on('data', (chunk) => {
        body += chunk
      })
      request.on('end', () => {
        receivePresence(JSON.parse(body))
        response.end('{"taskIds":[]}')
      })
    } else {
      assert.equal(request.url, '/regent/api/auth/me')
      response.end('{"id":1}')
    }
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  try {
    const { port } = server.address()
    agent = new DeviceAgent({
      stateFile: path.join(directory, 'state.json'),
      localPort: 12699,
    })
    const serverUrl = `http://127.0.0.1:${port}/regent`
    const status = await agent.pair(serverUrl, 'valid-device-token')
    assert.equal(status.paired, true)
    assert.equal(status.serverUrl, serverUrl)
    assert.equal(agent.allowOrigin(`http://127.0.0.1:${port}`), true)
    const presence = await Promise.race([
      presenceReceived,
      new Promise((_, reject) => setTimeout(() => reject(new Error('presence timeout')), 1000)),
    ])
    assert.equal(presence.deviceId, agent.deviceId)
    assert.equal(presence.agentInstanceId, agent.agentInstanceId)
  } finally {
    agent?.stopPresenceLoop()
    server.closeAllConnections()
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
  const receivedEvents = []
  const server = http.createServer((request, response) => {
    assert.equal(request.headers.authorization, 'Bearer device-token')
    if (request.url.endsWith('/events')) {
      let body = ''
      request.on('data', (chunk) => {
        body += chunk
      })
      request.on('end', () => {
        receivedEvents.push(...(JSON.parse(body).events || []))
        response.writeHead(200, { 'Content-Type': 'application/json' })
        response.end('1')
      })
      return
    }
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
    assert.ok(receivedEvents.some((event) => event.type === 'thread.started'))
  } finally {
    await new Promise((resolve) => server.close(resolve))
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('设备代理遇到损坏的续跑会话时自动新建会话', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-agent-fallback-'))
  const workdir = path.join(directory, 'project')
  fs.mkdirSync(workdir)
  const argsFile = path.join(directory, 'args.log')
  const fakeCodex = script(path.join(directory, 'bin/codex'), `#!/bin/sh
printf '%s\\n' "$*" >> "${argsFile}"
calls="$*"
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
case " $calls " in
  *" resume "*)
    printf '%s\\n' '{"type":"error","message":"{\\"error\\":{\\"message\\":\\"assistant tool_calls must be followed by tool messages\\"}}"}'
    exit 1
    ;;
esac
printf '%s\\n' '{"type":"thread.started","thread_id":"thread-fresh"}'
printf '%s\\n' '{"status":"DONE","summary":"新会话完成","changedFiles":[],"bugs":[]}' > "$output"
`)
  const server = http.createServer((request, response) => {
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
      localPort: 12697,
    })
    const result = await agent.runCodex({
      executablePath: fakeCodex,
      work: {
        workdir,
        images: [],
        environment: [],
      },
      instruction: '完成原始任务',
      sessionId: 'thread-broken',
      onThread: () => {},
    })

    assert.equal(result.exitCode, 0)
    assert.equal(result.threadId, 'thread-fresh')
    assert.equal(result.report.summary, '新会话完成')
    const calls = fs.readFileSync(argsFile, 'utf8').trim().split(/\r?\n/)
    assert.equal(calls.length, 2)
    assert.match(calls[0], /exec resume thread-broken/)
    assert.doesNotMatch(calls[1], / resume /)
    assert.match(result.rawResult, /新会话完成/)
  } finally {
    await new Promise((resolve) => server.close(resolve))
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('设备代理把 Codex JSONL 中的真实错误写进失败摘要', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-agent-diagnostic-'))
  const workdir = path.join(directory, 'project')
  fs.mkdirSync(workdir)
  const fakeCodex = script(path.join(directory, 'bin/codex'), `#!/bin/sh
cat >/dev/null
printf '%s\\n' '{"type":"error","message":"{\\"error\\":{\\"message\\":\\"CC Switch 拒绝了损坏的会话上下文\\"}}"}'
exit 1
`)
  const server = http.createServer((request, response) => {
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
      localPort: 12696,
    })
    const result = await agent.runCodex({
      executablePath: fakeCodex,
      work: {
        workdir,
        images: [],
        environment: [],
      },
      instruction: '完成原始任务',
      sessionId: null,
      onThread: () => {},
    })

    assert.equal(result.exitCode, 1)
    assert.match(result.report.summary, /CC Switch 拒绝了损坏的会话上下文/)
  } finally {
    await new Promise((resolve) => server.close(resolve))
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('执行位已满时设备代理会等待并自动重新认领', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-agent-capacity-'))
  let attempts = 0
  let queued = 0
  const claimDeviceIds = []
  const server = http.createServer(async (request, response) => {
    if (request.url.endsWith('/queue')) {
      queued += 1
      response.writeHead(204)
      response.end()
      return
    }
    let body = ''
    for await (const chunk of request) body += chunk
    claimDeviceIds.push(JSON.parse(body).deviceId)
    attempts += 1
    response.setHeader('Content-Type', 'application/json')
    if (attempts === 1) {
      response.writeHead(400)
      response.end(JSON.stringify({
        message: '平台同时运行的任务已达到上限 1，等有任务收场后再认领',
      }))
      return
    }
    response.writeHead(200)
    response.end(JSON.stringify({
      protocol: 'regent-agent/1',
      runId: 99,
      taskId: 165,
      attemptNo: 1,
      workdir: directory,
      instruction: '继续执行',
      sessionId: null,
      images: [],
      environment: [],
      plugins: [],
      leaseSeconds: 900,
    }))
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  try {
    const { port } = server.address()
    const agent = new DeviceAgent({
      stateFile: path.join(directory, 'state.json'),
      serverUrl: `http://127.0.0.1:${port}`,
      token: 'device-token',
      localPort: 12695,
      capacityRetryMillis: 10,
    })
    const job = {
      executor: 'regent-device@test',
      taskId: 165,
      projectCode: 'regent',
      message: '正在认领任务',
    }

    const work = await agent.claimWork(job)

    assert.equal(work.runId, 99)
    assert.equal(attempts, 2)
    assert.equal(queued, 1)
    assert.equal(claimDeviceIds.length, 2)
    assert.ok(claimDeviceIds.every((deviceId) => deviceId === agent.deviceId))
    assert.match(job.message, /任务已排队/)
  } finally {
    await new Promise((resolve) => server.close(resolve))
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('任务已终止时设备代理停止排队重试，不再持续打接口', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-agent-terminal-'))
  let claimAttempts = 0
  let queueAttempts = 0
  const server = http.createServer((request, response) => {
    response.setHeader('Content-Type', 'application/json')
    if (request.url.endsWith('/queue')) {
      queueAttempts += 1
      response.writeHead(400)
      response.end(JSON.stringify({
        code: 'DOMAIN_VIOLATION',
        message: '任务当前不能进入排队：CANCELLED',
      }))
      return
    }
    claimAttempts += 1
    response.writeHead(400)
    response.end(JSON.stringify({
      message: '平台同时运行的任务已达到上限 1，等有任务收场后再认领',
    }))
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  try {
    const { port } = server.address()
    const agent = new DeviceAgent({
      stateFile: path.join(directory, 'state.json'),
      serverUrl: `http://127.0.0.1:${port}`,
      token: 'device-token',
      localPort: 12694,
      capacityRetryMillis: 10,
    })

    await assert.rejects(
      () => agent.claimWork({
        executor: 'regent-device@test',
        taskId: 165,
        projectCode: 'regent',
        message: '正在认领任务',
      }),
      /已不能排队：任务当前不能进入排队：CANCELLED/,
    )
    assert.equal(claimAttempts, 1)
    assert.equal(queueAttempts, 1)
  } finally {
    await new Promise((resolve) => server.close(resolve))
    fs.rmSync(directory, { recursive: true, force: true })
  }
})
