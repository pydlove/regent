const { execFile, spawn } = require('node:child_process')
const crypto = require('node:crypto')
const fs = require('node:fs')
const http = require('node:http')
const os = require('node:os')
const path = require('node:path')
const { promisify } = require('node:util')

const execFileAsync = promisify(execFile)
const COMMON_BIN_DIRS = [
  '.local/bin',
  '.npm-global/bin',
  '.npm/bin',
  '.volta/bin',
  '.bun/bin',
  '.asdf/shims',
  'Library/pnpm',
  '.local/share/pnpm',
  '.local/share/mise/shims',
]
const LOGIN_SHELL_MARKER = '__REGENT_CODEX_PATH__='
const MAX_DIAGNOSTIC_CHARS = 2000
const MAX_EVENT_PAYLOAD_BYTES = 192 * 1024
const CAPACITY_RETRY_MILLIS = 5_000
const CAPACITY_RETRY_LIMIT_MILLIS = 30 * 60 * 1000
const releaseInfo = readJson(path.join(__dirname, 'release-info.json')) || {}

function codexFileNames(platform = process.platform) {
  return platform === 'win32'
    ? ['codex.exe', 'codex.cmd', 'codex.bat', 'codex']
    : ['codex']
}

function executable(candidate) {
  try {
    const normalized = path.resolve(candidate)
    const stat = fs.statSync(normalized)
    if (!stat.isFile()) return null
    fs.accessSync(normalized, fs.constants.X_OK)
    return normalized
  } catch {
    return null
  }
}

function codexInDirectory(directory, platform = process.platform) {
  for (const name of codexFileNames(platform)) {
    const found = executable(path.join(directory, name))
    if (found) return found
  }
  return null
}

function needsCommandShell(candidate) {
  return process.platform === 'win32' && /\.(?:cmd|bat)$/i.test(candidate)
}

function versionedNodeBins(root, layer = '') {
  try {
    return fs.readdirSync(root, { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .map((entry) => path.join(root, entry.name, layer, 'bin'))
      .filter((directory) => fs.existsSync(directory))
  } catch {
    return []
  }
}

function commonCandidates(home = os.homedir(), environment = process.env) {
  const candidates = COMMON_BIN_DIRS.map((directory) => path.join(home, directory))
  candidates.push('/opt/homebrew/bin', '/usr/local/bin', '/usr/bin')
  if (environment.APPDATA) candidates.push(path.join(environment.APPDATA, 'npm'))
  if (environment.LOCALAPPDATA) {
    candidates.push(path.join(environment.LOCALAPPDATA, 'Microsoft', 'WinGet', 'Links'))
  }
  candidates.push(...versionedNodeBins(path.join(home, '.nvm/versions/node')))
  candidates.push(...versionedNodeBins(
    path.join(home, '.local/share/fnm/node-versions'), 'installation'))
  candidates.push(...versionedNodeBins(path.join(home, '.local/share/mise/installs/node')))
  return candidates
}

async function loginShellCodex(userAgent = process.env.SHELL) {
  if (process.platform === 'win32') return null
  const shells = [userAgent, '/bin/zsh', '/bin/bash'].filter(Boolean)
  const lookup = `printf '\\n%s%s\\n' '${LOGIN_SHELL_MARKER}' "$(command -v -- "$1" 2>/dev/null || true)"`
  for (const shell of [...new Set(shells)]) {
    for (const mode of ['-lc', '-lic']) {
      try {
        const { stdout } = await execFileAsync(
          shell,
          [mode, lookup, 'regent-codex-detect', 'codex'],
          { timeout: 3000, maxBuffer: 64 * 1024 },
        )
        for (const line of String(stdout).split(/\r?\n/)) {
          if (!line.startsWith(LOGIN_SHELL_MARKER)) continue
          const found = executable(line.slice(LOGIN_SHELL_MARKER.length).trim())
          if (found) return found
        }
      } catch {
        // 登录 shell 可能没有配置文件、超时或被插件阻塞；继续下一个兜底位置。
      }
    }
  }
  return null
}

async function detectCodex() {
  const checkedAt = new Date().toISOString()
  const fromPath = String(process.env.PATH || '')
    .split(path.delimiter)
    .filter(Boolean)
    .map((directory) => codexInDirectory(directory))
    .find(Boolean)
  const fromShell = fromPath ? null : await loginShellCodex()
  const selected = fromPath || fromShell || commonCandidates()
    .map((directory) => codexInDirectory(directory))
    .find(Boolean)
  if (!selected) {
    return {
      available: false,
      executablePath: null,
      version: null,
      message: '当前设备未检测到 Codex',
      checkedAt,
    }
  }
  try {
    const { stdout } = await execFileAsync(selected, ['--version'], {
      timeout: 5000,
      maxBuffer: 256 * 1024,
      shell: needsCommandShell(selected),
      env: {
        ...process.env,
        PATH: `${path.dirname(selected)}${path.delimiter}${process.env.PATH || ''}`,
      },
    })
    const version = String(stdout).trim().split(/\r?\n/)[0] || null
    return {
      available: true,
      executablePath: selected,
      version,
      message: version
        ? 'Codex 可执行文件可用'
        : 'Codex 可执行文件可用，但 --version 没有返回版本号',
      checkedAt,
    }
  } catch (error) {
    return {
      available: false,
      executablePath: selected,
      version: null,
      message: `Codex 无法正常执行：${error.message}`,
      checkedAt,
    }
  }
}

function readJson(file) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'))
  } catch {
    return null
  }
}

function serverEndpoint(serverUrl, pathname) {
  return new URL(pathname.replace(/^\/+/, ''), `${serverUrl.replace(/\/+$/, '')}/`).toString()
}

function readableDiagnostic(value) {
  if (value == null) return null
  const text = String(value).trim()
  if (!text) return null
  try {
    const parsed = JSON.parse(text)
    const nested = parsed?.error?.message
    if (typeof nested === 'string' && nested.trim()) return nested.trim()
    if (typeof parsed?.message === 'string' && parsed.message.trim()) return parsed.message.trim()
  } catch {
    // 不一定是 JSON，原样保留即可。
  }
  return text
}

function codexEventDiagnostic(event) {
  if (event?.type === 'error') return readableDiagnostic(event.message)
  if (event?.type === 'turn.failed') {
    return readableDiagnostic(event.error?.message ?? event.message)
  }
  return null
}

function stderrDiagnostic(text) {
  if (!text) return null
  const lines = String(text).split(/\r?\n/).map((line) => line.trim()).filter(Boolean)
  if (!lines.length) return null
  const tail = lines.slice(-8).join('\n')
  return tail.length <= MAX_DIAGNOSTIC_CHARS
    ? tail
    : tail.slice(tail.length - MAX_DIAGNOSTIC_CHARS)
}

function diagnosticSummary(prefix, diagnostic) {
  if (!diagnostic) return `${prefix}，执行器没有返回错误说明`
  const text = diagnostic.length <= MAX_DIAGNOSTIC_CHARS
    ? diagnostic
    : diagnostic.slice(0, MAX_DIAGNOSTIC_CHARS)
  return `${prefix}：${text}`
}

function resumeContextInvalid(diagnostic) {
  if (!diagnostic) return false
  const text = diagnostic.toLowerCase()
  const orphanToolCall = text.includes('tool_calls')
    && (text.includes('tool messages') || text.includes('tool_call_id'))
  return orphanToolCall
    || text.includes('session not found')
    || text.includes('conversation not found')
    || text.includes('no rollout found')
    || text.includes('thread/resume failed')
    || text.includes('failed to resume session')
}

function isTemporaryClaimFailure(message) {
  const text = String(message || '')
  return text.includes('平台同时运行的任务已达到上限')
}

class DeviceAgent {
  constructor({
    stateFile,
    serverUrl = null,
    token = null,
    localPort = 12601,
    capacityRetryMillis = CAPACITY_RETRY_MILLIS,
    capacityRetryLimitMillis = CAPACITY_RETRY_LIMIT_MILLIS,
    maxConcurrentTasks = Number(process.env.REGENT_DEVICE_MAX_CONCURRENCY || 1),
  }) {
    this.stateFile = stateFile
    this.serverUrl = serverUrl
    this.token = token
    this.localPort = localPort
    this.capacityRetryMillis = capacityRetryMillis
    this.capacityRetryLimitMillis = capacityRetryLimitMillis
    this.maxConcurrentTasks = Number.isInteger(maxConcurrentTasks) && maxConcurrentTasks > 0
      ? maxConcurrentTasks
      : 1
    this.localServer = null
    this.jobs = new Map()
    this.activeTasks = new Set()
    this.presenceTimer = null
    this.deviceId = crypto.randomUUID()
    this.agentInstanceId = crypto.randomUUID()
    this.codexStatus = null
    this.codexCheckPromise = null
  }

  loadState() {
    const saved = readJson(this.stateFile) || {}
    if (!this.serverUrl && typeof saved.serverUrl === 'string') this.serverUrl = saved.serverUrl
    if (!this.token && typeof saved.token === 'string') this.token = saved.token
    if (typeof saved.deviceId === 'string') this.deviceId = saved.deviceId
    if (this.token) this.startPresenceLoop()
  }

  saveState() {
    fs.mkdirSync(path.dirname(this.stateFile), { recursive: true, mode: 0o700 })
    fs.writeFileSync(this.stateFile, `${JSON.stringify({
      serverUrl: this.serverUrl,
      token: this.token,
      deviceId: this.deviceId,
    }, null, 2)}\n`, { mode: 0o600 })
  }

  configure(serverUrl, token) {
    this.serverUrl = serverUrl
    this.token = token
    this.saveState()
    this.startPresenceLoop()
  }

  async pair(serverUrl, token) {
    const normalized = new URL(String(serverUrl || '').trim())
    if (!['http:', 'https:'].includes(normalized.protocol)) {
      throw new Error('服务器地址必须使用 http 或 https')
    }
    if (!token || typeof token !== 'string') throw new Error('缺少设备令牌')
    normalized.search = ''
    normalized.hash = ''
    const normalizedBase = normalized.href.replace(/\/+$/, '')
    const response = await fetch(serverEndpoint(normalizedBase, '/api/auth/me'), {
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'application/json',
      },
      signal: AbortSignal.timeout(5000),
    })
    if (!response.ok) throw new Error('设备令牌无效或已经过期')
    this.configure(normalizedBase, token)
    return this.status()
  }

  async authenticated(pathname, options = {}) {
    if (!this.serverUrl || !this.token) throw new Error('设备代理尚未配对服务器')
    return fetch(serverEndpoint(this.serverUrl, pathname), {
      ...options,
      headers: {
        Authorization: `Bearer ${this.token}`,
        Accept: 'application/json',
        'X-Regent-Relay-Request': '1',
        ...(options.headers || {}),
      },
    })
  }

  async validatePairing() {
    if (!this.serverUrl || !this.token) return false
    try {
      const response = await this.authenticated('/api/auth/me', {
        signal: AbortSignal.timeout(5000),
      })
      return response.ok
    } catch {
      return false
    }
  }

  async codexStatusForDisplay() {
    if (this.codexStatus && Date.now() - Date.parse(this.codexStatus.checkedAt) < 30_000) {
      return this.codexStatus
    }
    if (!this.codexCheckPromise) {
      this.codexCheckPromise = detectCodex()
        .then((status) => {
          this.codexStatus = status
          return status
        })
        .finally(() => {
          this.codexCheckPromise = null
        })
    }
    return this.codexCheckPromise
  }

  async status() {
    const codex = await this.codexStatusForDisplay()
    const paired = await this.validatePairing()
    return {
      paired,
      deviceId: this.deviceId,
      serverUrl: this.serverUrl,
      hostname: os.hostname(),
      platform: process.platform,
      executor: `regent-device@${os.hostname()}`,
      clientRelease: releaseInfo.releaseCode || null,
      shellRelease: global.regentRuntime?.shellRelease || null,
      codex: paired
        ? codex
        : {
            available: false,
            executablePath: null,
            version: null,
            message: '设备代理已运行，等待当前浏览器完成配对',
            checkedAt: codex.checkedAt,
          },
    }
  }

  async startLocalServer() {
    if (this.localServer) return
    this.localServer = http.createServer(async (request, response) => {
      const origin = request.headers.origin
      const url = new URL(request.url, `http://127.0.0.1:${this.localPort}`)
      const pairPath = url.pathname === '/pair'
      const pairing = pairPath && ['POST', 'OPTIONS'].includes(request.method)
      const publicStatus = ['GET', 'OPTIONS'].includes(request.method) && url.pathname === '/status'
      // Electron 主进程、CLI 等本机进程不受浏览器同源策略约束，不会携带 Origin。
      // 浏览器发起跨域请求时一定带 Origin，因此仍然必须通过 allowOrigin 校验。
      const localProcess = !origin
      if (this.allowOrigin(origin) || pairing || publicStatus || localProcess) {
        if (origin) response.setHeader('Access-Control-Allow-Origin', origin)
        response.setHeader(
          'Vary',
          'Origin, Access-Control-Request-Private-Network, Access-Control-Request-Local-Network',
        )
        response.setHeader(
          'Access-Control-Allow-Headers',
          'Content-Type, Private-Network-Access-Name, Private-Network-Access-ID',
        )
        response.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        response.setHeader('Access-Control-Allow-Private-Network', 'true')
        response.setHeader('Access-Control-Allow-Local-Network', 'true')
      }
      if (request.method === 'OPTIONS') {
        response.writeHead(
          this.allowOrigin(origin) || pairing || publicStatus || localProcess ? 204 : 403,
        )
        response.end()
        return
      }
      if (pairPath && request.method === 'POST') {
        try {
          const body = await this.readBody(request)
          this.sendJson(response, 200, await this.pair(body.serverUrl, body.token))
        } catch (error) {
          this.sendJson(response, 400, { error: error.message })
        }
        return
      }
      if (!this.allowOrigin(origin) && !publicStatus && !localProcess) {
        this.sendJson(response, 403, { error: '来源不允许访问设备代理' })
        return
      }

      if (request.method === 'GET' && url.pathname === '/status') {
        this.sendJson(response, 200, await this.status())
        return
      }
      if (request.method === 'POST' && url.pathname === '/run-task') {
        try {
          const body = await this.readBody(request)
          const job = this.runTask(body)
          this.sendJson(response, 202, job)
        } catch (error) {
          this.sendJson(response, 400, { error: error.message })
        }
        return
      }
      if (request.method === 'GET' && url.pathname.startsWith('/jobs/')) {
        const job = this.jobs.get(url.pathname.slice('/jobs/'.length))
        this.sendJson(response, job ? 200 : 404, job || { error: '任务不存在' })
        return
      }
      this.sendJson(response, 404, { error: '接口不存在' })
    })
    await new Promise((resolve, reject) => {
      this.localServer.once('error', reject)
      this.localServer.listen(this.localPort, '127.0.0.1', resolve)
    })
  }

  stopLocalServer() {
    this.stopPresenceLoop()
    this.localServer?.close()
    this.localServer = null
  }

  stopPresenceLoop() {
    if (this.presenceTimer) clearInterval(this.presenceTimer)
    this.presenceTimer = null
  }

  allowOrigin(origin) {
    if (!origin) return false
    try {
      const parsed = new URL(origin)
      if (['127.0.0.1', 'localhost'].includes(parsed.hostname)) return true
      return Boolean(this.serverUrl && parsed.origin === new URL(this.serverUrl).origin)
    } catch {
      return false
    }
  }

  sendJson(response, status, body) {
    const content = `${JSON.stringify(body)}\n`
    response.writeHead(status, {
      'Content-Type': 'application/json; charset=UTF-8',
      'Content-Length': Buffer.byteLength(content),
      'Cache-Control': 'no-store',
    })
    response.end(content)
  }

  readBody(request) {
    return new Promise((resolve, reject) => {
      const chunks = []
      let size = 0
      request.on('data', (chunk) => {
        size += chunk.length
        if (size > 1024 * 1024) {
          reject(new Error('请求内容过大'))
          request.destroy()
        } else {
          chunks.push(chunk)
        }
      })
      request.on('end', () => {
        try {
          resolve(chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {})
        } catch {
          reject(new Error('请求 JSON 格式不正确'))
        }
      })
      request.on('error', reject)
    })
  }

  runTask({ taskId, projectCode }) {
    if (!Number.isInteger(Number(taskId)) || Number(taskId) <= 0) {
      throw new Error('缺少有效任务号')
    }
    if (this.activeTasks.has(Number(taskId))) throw new Error('该任务正在当前设备执行')
    if (this.activeTasks.size >= this.maxConcurrentTasks) {
      throw new Error(`当前设备最多同时执行 ${this.maxConcurrentTasks} 个任务`)
    }
    const id = crypto.randomUUID()
    const job = {
      id,
      taskId: Number(taskId),
      projectCode,
      status: 'running',
      message: '正在认领任务',
      startedAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }
    this.activeTasks.add(Number(taskId))
    this.jobs.set(id, job)
    this.executeJob(job)
      .catch((error) => {
        job.status = 'failed'
        job.message = error.message
        job.updatedAt = new Date().toISOString()
      })
      .finally(() => this.activeTasks.delete(Number(taskId)))
    return job
  }

  startPresenceLoop() {
    if (this.presenceTimer) return
    const heartbeat = async () => {
      if (!this.serverUrl || !this.token) return
      try {
        const current = await this.status()
        if (!current.paired) return
        const response = await this.authenticated('/api/device-agent/presence', {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${this.token}`,
            Accept: 'application/json',
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            deviceId: this.deviceId,
            hostname: current.hostname,
            platform: current.platform,
            executor: current.executor,
            agentInstanceId: this.agentInstanceId,
            codex: current.codex,
            clientRelease: current.clientRelease,
            shellRelease: current.shellRelease,
          }),
        })
        if (!response.ok) return
        const presence = await response.json()
        for (const taskId of presence.taskIds || []) {
          if (this.activeTasks.size >= this.maxConcurrentTasks) break
          try {
            this.runTask({ taskId, projectCode: null })
          } catch {
            // 同一任务已在执行时忽略重复下发。
          }
        }
      } catch {
        // 服务器短暂不可达时等下一拍；任务仍在平台队列中。
      }
    }
    void heartbeat()
    this.presenceTimer = setInterval(() => void heartbeat(), 10_000)
  }

  async executeJob(job) {
    const status = await this.status()
    if (!status.paired) throw new Error('设备代理尚未配对服务器')
    if (!status.codex.available || !status.codex.executablePath) {
      throw new Error(status.codex.message)
    }

    job.executor = status.executor
    job.message = '正在认领任务'
    const work = await this.claimWork(job)
    job.runId = work.runId
    job.message = '本机 Codex 正在执行'

    let instruction = work.instruction
    let sessionId = work.sessionId || null
    const heartbeatEvery = Math.max(15_000, Math.floor((work.leaseSeconds || 900) * 1000 / 3))
    const heartbeat = setInterval(() => void this.heartbeat(work, job), heartbeatEvery)
    try {
      for (let round = 0; round < 6; round += 1) {
        const result = await this.runCodex({
          executablePath: status.codex.executablePath,
          executor: job.executor,
          work,
          instruction,
          sessionId,
          onThread: (threadId) => {
            if (threadId) sessionId = threadId
          },
        })
        if (result.threadId) sessionId = result.threadId
        const report = await this.report(work, job, result, sessionId)
        job.message = report.note || report.outcome
        job.updatedAt = new Date().toISOString()
        if (report.outcome !== 'CONTINUE') {
          job.status = report.outcome === 'DONE' ? 'success' : report.outcome === 'FAILED' ? 'failed' : 'blocked'
          return
        }
        instruction = report.instruction
      }
      throw new Error('连续自动决策轮次超过设备代理上限')
    } finally {
      clearInterval(heartbeat)
    }
  }

  async claimWork(job) {
    const startedAt = Date.now()
    while (true) {
      const claimResponse = await this.authenticated('/api/device-agent/tasks/claim', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${this.token}`,
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          executor: job.executor,
          sessionId: crypto.randomUUID(),
          projectCode: job.projectCode,
          taskId: job.taskId,
          deviceId: this.deviceId,
        }),
      })
      const claimText = await claimResponse.text()
      if (claimResponse.ok) return JSON.parse(claimText)

      let message = claimText
      try {
        message = JSON.parse(claimText).message || message
      } catch {
        // 保留原始响应
      }
      message ||= `认领失败（HTTP ${claimResponse.status}）`
      if (!isTemporaryClaimFailure(message)) {
        throw new Error(claimResponse.status === 204 ? '任务当前不可领取' : message)
      }
      if (Date.now() - startedAt >= this.capacityRetryLimitMillis) {
        throw new Error(`等待执行位超过 30 分钟：${message}`)
      }
      const queueResult = await this.markTaskQueued(job)
      if (queueResult.terminal) {
        throw new Error(`任务 #${job.taskId} 已不能排队：${queueResult.message}`)
      }
      job.message = `任务已排队，正在等待：${message}`
      job.updatedAt = new Date().toISOString()
      await new Promise((resolve) => setTimeout(resolve, this.capacityRetryMillis))
    }
  }

  async markTaskQueued(job) {
    try {
      const response = await this.authenticated(
        `/api/device-agent/tasks/${job.taskId}/queue`,
        {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${this.token}`,
            Accept: 'application/json',
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ deviceId: this.deviceId }),
        },
      )
      const text = await response.text()
      if (!response.ok) {
        let message = text
        try {
          message = JSON.parse(text).message || message
        } catch {
          // 保留原始响应
        }
        message ||= `进入排队状态失败（HTTP ${response.status}）`
        console.warn(`任务 #${job.taskId} 进入排队状态失败：HTTP ${response.status} ${message}`)
        return {
          ok: false,
          terminal: [400, 401, 403, 404].includes(response.status),
          message,
        }
      }
      return { ok: true, terminal: false, message: '' }
    } catch (error) {
      console.warn(`任务 #${job.taskId} 进入排队状态失败：${error.message}`)
      return { ok: false, terminal: false, message: error.message }
    }
  }

  async heartbeat(work, job) {
    try {
      const response = await this.authenticated(`/api/device-agent/runs/${work.runId}/heartbeat`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${this.token}`,
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ executor: job.executor, deviceId: this.deviceId }),
      })
      if (!response.ok) return
      const snapshot = await response.json()
      if (snapshot.nextAction === 'STOP') job.message = '平台已终止该任务'
      else if (snapshot.nextAction === 'WAIT_DECISION') job.message = '等待人工批准'
    } catch {
      // 单次心跳失败不打断本机进程；租约到期前下一次仍会续租。
    }
  }

  async appendEvents(executor, work, events) {
    const agentExecutor = executor || `regent-device@${os.hostname()}`
    const response = await this.authenticated(`/api/device-agent/runs/${work.runId}/events`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${this.token}`,
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        executor: agentExecutor,
        deviceId: this.deviceId,
        events,
      }),
    })
    if (!response.ok) {
      const text = await response.text()
      let message = text
      try {
        message = JSON.parse(text).message || message
      } catch {
        // 保留原始响应
      }
      throw new Error(message || `事件上报失败（HTTP ${response.status}）`)
    }
    return response.json().catch(() => 0)
  }

  async report(work, job, result, sessionId) {
    const body = {
      executor: job.executor,
      deviceId: this.deviceId,
      sessionId,
      status: result.report.status || (result.exitCode === 0 ? 'DONE' : 'FAILED'),
      summary: result.report.summary || (result.exitCode === 0 ? '本机 Codex 执行完成' : '本机 Codex 执行失败'),
      changedFiles: Array.isArray(result.report.changedFiles) ? result.report.changedFiles : [],
      decision: result.report.decision ?? null,
      bugs: Array.isArray(result.report.bugs) ? result.report.bugs : [],
      tokens: result.report.tokens ?? null,
      exitCode: result.exitCode,
      rawResult: result.rawResult,
    }
    const response = await this.authenticated(`/api/device-agent/runs/${work.runId}/report`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${this.token}`,
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
    })
    const text = await response.text()
    if (!response.ok) {
      let message = text
      try {
        message = JSON.parse(text).message || message
      } catch {
        // 保留原始响应
      }
      throw new Error(message || `上报失败（HTTP ${response.status}）`)
    }
    return JSON.parse(text)
  }

  async contract() {
    const response = await this.authenticated('/api/device-agent/contract')
    if (!response.ok) throw new Error('无法读取设备执行协议')
    return response.json()
  }

  async runCodex({ executablePath, executor, work, instruction, sessionId, onThread }) {
    const result = await this.runCodexAttempt({
      executablePath,
      executor,
      work,
      instruction,
      sessionId,
      onThread,
    })
    if (!sessionId || result.exitCode === 0 || !resumeContextInvalid(result.diagnostic)) {
      return result
    }

    console.warn(`Codex 会话 ${sessionId} 的上下文无法恢复，改用新会话重试`)
    return this.runCodexAttempt({
      executablePath,
      executor,
      work,
      instruction: `原 Codex 会话的上下文无法恢复，继续恢复会被模型服务拒绝。下面是一次完整的新会话，`
        + `请根据当前工作区与完整任务要求继续，先检查已经完成的改动，不要推倒重做。\n\n${instruction}`,
      sessionId: null,
      onThread,
    })
  }

  async runCodexAttempt({ executablePath, executor, work, instruction, sessionId, onThread }) {
    const workdir = work.workdir
    if (!fs.existsSync(workdir) || !fs.statSync(workdir).isDirectory()) {
      throw new Error(`当前设备不存在项目目录：${workdir}`)
    }
    const contract = await this.contract()
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-device-agent-'))
    const schemaPath = path.join(directory, 'schema.json')
    const outputPath = path.join(directory, 'output.json')
    const promptPath = path.join(directory, 'prompt.txt')
    const errorPath = path.join(directory, 'stderr.log')
    fs.writeFileSync(schemaPath, `${JSON.stringify(contract.resultSchema, null, 2)}\n`, 'utf8')
    fs.writeFileSync(promptPath, instruction, 'utf8')

    const args = ['exec']
    if (sessionId) {
      args.push('resume', sessionId, '-c', 'sandbox_mode="workspace-write"')
    } else {
      args.push('-C', workdir, '-s', 'workspace-write')
    }
    args.push('--skip-git-repo-check')
    for (const image of work.images || []) {
      if (fs.existsSync(image.path)) args.push('-i', image.path)
    }
    args.push('--json', '--output-schema', schemaPath, '-o', outputPath, '-')

    const environment = { ...process.env }
    for (const variable of work.environment || []) environment[variable.key] = variable.value
    environment.PATH = `${path.dirname(executablePath)}${path.delimiter}${environment.PATH || ''}`
    const errorStream = fs.createWriteStream(errorPath)
    const child = spawn(executablePath, args, {
      cwd: workdir,
      env: environment,
      shell: needsCommandShell(executablePath),
      stdio: ['pipe', 'pipe', 'pipe'],
    })
    child.stderr.pipe(errorStream)
    child.stdin.end(fs.readFileSync(promptPath, 'utf8'))
    let threadId = sessionId
    const diagnostics = []
    const pendingEvents = []
    let eventTimer = null
    let eventUpload = Promise.resolve()
    const flushEvents = async () => {
      if (eventTimer) {
        clearTimeout(eventTimer)
        eventTimer = null
      }
      const batch = pendingEvents.splice(0)
      if (batch.length > 0) {
        eventUpload = eventUpload
          .then(() => this.appendEvents(executor, work, batch))
          .catch((error) => {
            console.warn(`Codex 事件上报失败：${error.message}`)
          })
      }
      await eventUpload
    }
    const queueEvent = (type, payload) => {
      let eventType = type || 'raw'
      let eventPayload = payload
      if (Buffer.byteLength(eventPayload, 'utf8') > MAX_EVENT_PAYLOAD_BYTES) {
        eventPayload = JSON.stringify({
          type: 'truncated_event',
          originalType: eventType,
          preview: eventPayload.slice(0, 64 * 1024),
          originalBytes: Buffer.byteLength(eventPayload, 'utf8'),
        })
        eventType = 'raw'
      }
      pendingEvents.push({
        type: eventType,
        payload: eventPayload,
        occurredAt: new Date().toISOString(),
      })
      if (pendingEvents.length >= 40) {
        void flushEvents()
      } else if (!eventTimer) {
        eventTimer = setTimeout(() => void flushEvents(), 750)
      }
    }
    let stdoutBuffer = ''
    child.stdout.on('data', (chunk) => {
      stdoutBuffer += chunk.toString('utf8')
      const lines = stdoutBuffer.split(/\r?\n/)
      stdoutBuffer = lines.pop() || ''
      for (const line of lines) {
        if (!line.trim()) continue
        try {
          const event = JSON.parse(line)
          queueEvent(event.type, line)
          if (event.thread_id) {
            threadId = event.thread_id
            onThread?.(threadId)
          }
          const diagnostic = codexEventDiagnostic(event)
          if (diagnostic) diagnostics.push(diagnostic)
        } catch {
          queueEvent('raw', JSON.stringify(line))
        }
      }
    })
    const timeout = setTimeout(() => {
      child.kill('SIGTERM')
    }, 30 * 60 * 1000)
    const exitCode = await new Promise((resolve, reject) => {
      child.on('error', reject)
      child.on('close', resolve)
    }).finally(() => clearTimeout(timeout))
    await flushEvents()
    await new Promise((resolve) => errorStream.end(resolve))
    const rawResult = fs.existsSync(outputPath) ? fs.readFileSync(outputPath, 'utf8') : ''
    const stderr = fs.existsSync(errorPath) ? fs.readFileSync(errorPath, 'utf8') : ''
    const diagnostic = diagnostics.at(-1) || stderrDiagnostic(stderr)
    let parsed = {}
    try {
      parsed = JSON.parse(rawResult)
    } catch {
      parsed = {
        status: 'FAILED',
        summary: exitCode === 0
          ? diagnosticSummary('Codex 没有返回约定 JSON', diagnostic)
          : diagnosticSummary(`Codex 执行失败（退出码 ${exitCode}）`, diagnostic),
        changedFiles: [],
        bugs: [],
      }
    } finally {
      fs.rmSync(directory, { recursive: true, force: true })
    }
    return { exitCode, threadId, report: parsed, rawResult, diagnostic }
  }
}

module.exports = {
  DeviceAgent,
  codexInDirectory,
  commonCandidates,
  detectCodex,
  executable,
  loginShellCodex,
  resumeContextInvalid,
}
