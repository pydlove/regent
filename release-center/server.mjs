import { spawn } from 'node:child_process'
import crypto from 'node:crypto'
import fs from 'node:fs'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

import {
  codexPrompt,
  codexSchema,
  nextVersion,
  normalizeDraft,
  normalizeReleasePreview,
  normalizeReviewedRelease,
  parseVersion,
} from './lib.mjs'

const centerDirectory = path.dirname(fileURLToPath(import.meta.url))
const projectDirectory = path.resolve(centerDirectory, '..')
const publicDirectory = path.join(centerDirectory, 'public')
const releasesPath = path.join(projectDirectory, 'regent-web/src/content/releases.json')
const releaseScript = path.join(projectDirectory, 'scripts/release-version.mjs')
const runtimeDirectory = path.join(
  process.env.REGENT_HOME?.trim() || path.join(os.homedir(), '.regent'),
  'release-center',
)
const draftPath = path.join(runtimeDirectory, 'draft.json')
const lastJobPath = path.join(runtimeDirectory, 'last-job.json')
const port = Number(process.env.REGENT_RELEASE_CENTER_PORT || 12600)
const host = '127.0.0.1'
const jobs = new Map()
let activeJobId = null
let generating = false

fs.mkdirSync(runtimeDirectory, { recursive: true, mode: 0o700 })

function persistJob(job) {
  fs.writeFileSync(lastJobPath, `${JSON.stringify(job, null, 2)}\n`, { mode: 0o600 })
}

function loadLastJob() {
  try {
    const job = JSON.parse(fs.readFileSync(lastJobPath, 'utf8'))
    if (job?.id && job?.version) jobs.set(job.id, job)
  } catch {
    // 还没有历史发布任务。
  }
}

loadLastJob()

function releases() {
  return JSON.parse(fs.readFileSync(releasesPath, 'utf8')).releases
}

function readDraft() {
  try {
    if (!fs.existsSync(draftPath)) return null
    const parsed = JSON.parse(fs.readFileSync(draftPath, 'utf8'))
    const draft = normalizeDraft(parsed)
    if (releases().some((release) => release.version === draft.version)) {
      clearDraft()
      return null
    }
    return {
      ...draft,
      gitHead: typeof parsed.gitHead === 'string' ? parsed.gitHead : '',
      generatedAt: typeof parsed.generatedAt === 'string' ? parsed.generatedAt : '',
      updatedAt: typeof parsed.updatedAt === 'string' ? parsed.updatedAt : '',
    }
  } catch (error) {
    console.warn(`发布草稿读取失败，已忽略：${error.message}`)
    clearDraft()
    return null
  }
}

function writeDraft(value, { existing = readDraft(), gitHead = '' } = {}) {
  const draft = normalizeDraft(value)
  const now = new Date().toISOString()
  const sameVersion = existing?.version === draft.version
  const record = {
    ...draft,
    gitHead: sameVersion ? existing.gitHead || gitHead : gitHead,
    generatedAt: sameVersion ? existing.generatedAt || now : now,
    updatedAt: now,
  }
  fs.mkdirSync(runtimeDirectory, { recursive: true, mode: 0o700 })
  const temporary = `${draftPath}.${process.pid}.${Date.now()}.tmp`
  fs.writeFileSync(temporary, `${JSON.stringify(record, null, 2)}\n`, { mode: 0o600 })
  fs.renameSync(temporary, draftPath)
  return record
}

function clearDraft() {
  fs.rmSync(draftPath, { force: true })
}

function relayConfiguration() {
  const regentHome = process.env.REGENT_HOME?.trim() || path.join(os.homedir(), '.regent')
  const envFile = path.join(regentHome, 'relay-connector.env')
  const content = fs.readFileSync(envFile, 'utf8')
  const serverUrl = content.match(/^REGENT_RELAY_SERVER_URL=(.+)$/m)?.[1]?.trim()
  if (!serverUrl) throw new Error('relay-connector.env 缺少 REGENT_RELAY_SERVER_URL')
  const url = new URL(serverUrl)
  const environment = { ...process.env }
  environment.REGENT_SERVER_IP ||= url.hostname
  environment.REGENT_SERVER_USER ||= 'root'
  environment.REGENT_SSH_PORT ||= '22'
  const key = path.join(os.homedir(), '.ssh/id_rsa')
  if (!environment.REGENT_SSH_KEY && fs.existsSync(key)) environment.REGENT_SSH_KEY = key
  return { url: url.toString().replace(/\/$/, ''), environment }
}

function sendJson(response, status, value) {
  const body = `${JSON.stringify(value)}\n`
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=UTF-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store',
  })
  response.end(body)
}

function sendError(response, status, message) {
  sendJson(response, status, { error: message })
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    let size = 0
    const chunks = []
    request.on('data', (chunk) => {
      size += chunk.length
      if (size > 1024 * 1024) {
        reject(new Error('请求内容过大'))
        request.destroy()
        return
      }
      chunks.push(chunk)
    })
    request.on('end', () => {
      if (chunks.length === 0) {
        resolve({})
        return
      }
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')))
      } catch {
        reject(new Error('请求 JSON 格式不正确'))
      }
    })
    request.on('error', reject)
  })
}

function capture(command, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd: projectDirectory,
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    let stdout = ''
    let stderr = ''
    child.stdout.on('data', (chunk) => {
      stdout += chunk.toString('utf8')
    })
    child.stderr.on('data', (chunk) => {
      stderr += chunk.toString('utf8')
    })
    child.on('error', reject)
    child.on('close', (code) => {
      if (code === 0) resolve(stdout.trim())
      else reject(new Error(stderr.trim() || `${command} 退出码 ${code}`))
    })
  })
}

function parseCodexOutput(text) {
  const trimmed = text.trim()
  const withoutFence = trimmed
    .replace(/^```(?:json)?\s*/i, '')
    .replace(/\s*```$/, '')
  return normalizeReleasePreview(JSON.parse(withoutFence))
}

function runCodex(version, previousVersion, instruction) {
  return new Promise((resolve, reject) => {
    const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-release-codex-'))
    const schemaPath = path.join(temporaryDirectory, 'release-schema.json')
    const outputPath = path.join(temporaryDirectory, 'release-output.json')
    fs.writeFileSync(schemaPath, `${JSON.stringify(codexSchema(), null, 2)}\n`, 'utf8')
    const executable = process.env.REGENT_CODEX_COMMAND || 'codex'
    const args = [
      'exec',
      '-C',
      projectDirectory,
      '-s',
      'read-only',
      '--skip-git-repo-check',
      '--ephemeral',
      '--json',
      '--output-schema',
      schemaPath,
      '-o',
      outputPath,
      '-',
    ]
    const child = spawn(executable, args, {
      cwd: projectDirectory,
      stdio: ['pipe', 'pipe', 'pipe'],
      env: process.env,
    })
    let stderr = ''
    let settled = false
    const timer = setTimeout(() => {
      if (settled) return
      settled = true
      child.kill('SIGTERM')
      fs.rmSync(temporaryDirectory, { recursive: true, force: true })
      reject(new Error('Codex 生成超时（10 分钟）'))
    }, 10 * 60 * 1000)
    child.stderr.on('data', (chunk) => {
      stderr += chunk.toString('utf8')
      if (stderr.length > 20000) stderr = stderr.slice(-20000)
    })
    child.stdout.resume()
    child.on('error', (error) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      fs.rmSync(temporaryDirectory, { recursive: true, force: true })
      reject(error)
    })
    child.on('close', (code) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      try {
        if (code !== 0) throw new Error(stderr.trim() || `Codex 退出码 ${code}`)
        if (!fs.existsSync(outputPath)) throw new Error('Codex 没有生成结构化结果')
        resolve(parseCodexOutput(fs.readFileSync(outputPath, 'utf8')))
      } catch (error) {
        reject(error)
      } finally {
        fs.rmSync(temporaryDirectory, { recursive: true, force: true })
      }
    })
    child.stdin.end(codexPrompt({ version, previousVersion, instruction }))
  })
}

function appendJobLog(job, chunk) {
  const text = chunk.toString('utf8')
  for (const line of text.split(/\r?\n/)) {
    if (line) job.logs.push(line.slice(0, 2000))
  }
  if (job.logs.length > 2000) job.logs.splice(0, job.logs.length - 2000)
  job.updatedAt = new Date().toISOString()
  persistJob(job)
}

function startPublish(payload) {
  if (activeJobId) throw new Error('已有发布任务正在执行')
  const parsed = parseVersion(payload.version)
  const content = normalizeReviewedRelease(payload)
  const current = releases()
  if (current.some((release) => release.version === parsed.version)) {
    throw new Error(`版本 ${parsed.version} 已存在，请递增构建号`)
  }
  const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-release-publish-'))
  const releaseInfoFile = path.join(temporaryDirectory, 'release-info.json')
  fs.writeFileSync(releaseInfoFile, `${JSON.stringify(content, null, 2)}\n`, 'utf8')
  const args = [
    releaseScript,
    '--version',
    parsed.version,
    '--release-info-file',
    releaseInfoFile,
  ]
  if (payload.deploy === true) args.push('--deploy')
  if (payload.pushTag === true) args.push('--push-tag')

  const job = {
    id: crypto.randomUUID(),
    version: parsed.version,
    status: 'running',
    logs: [],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    error: null,
  }
  jobs.set(job.id, job)
  activeJobId = job.id
  job.logs.push(`开始发布 ${parsed.version}`)
  persistJob(job)
  const child = spawn(process.execPath, args, {
    cwd: projectDirectory,
    stdio: ['ignore', 'pipe', 'pipe'],
    env: process.env,
  })
  child.stdout.on('data', (chunk) => appendJobLog(job, chunk))
  child.stderr.on('data', (chunk) => appendJobLog(job, chunk))
  child.on('error', (error) => {
    job.status = 'failed'
    job.error = error.message
    job.updatedAt = new Date().toISOString()
    activeJobId = null
    persistJob(job)
    fs.rmSync(temporaryDirectory, { recursive: true, force: true })
  })
  child.on('close', (code) => {
    job.status = code === 0 ? 'success' : 'failed'
    job.error = code === 0 ? null : `发布进程退出码 ${code}`
    job.updatedAt = new Date().toISOString()
    activeJobId = null
    persistJob(job)
    fs.rmSync(temporaryDirectory, { recursive: true, force: true })
    if (code === 0) clearDraft()
  })
  return job
}

function startServerDeploy() {
  if (activeJobId) throw new Error('已有发布任务正在执行')
  const { url, environment } = relayConfiguration()
  const deployScript = path.join(projectDirectory, 'deploy/remote/deploy.sh')
  if (!fs.existsSync(deployScript)) throw new Error('找不到公网部署脚本')
  const job = {
    id: crypto.randomUUID(),
    version: '中继器与服务器',
    kind: 'server-deploy',
    title: '发布中继器并升级服务器',
    status: 'running',
    logs: [`开始发布 ${url}`],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    error: null,
  }
  jobs.set(job.id, job)
  activeJobId = job.id
  persistJob(job)
  const child = spawn(deployScript, [], {
    cwd: projectDirectory,
    stdio: ['ignore', 'pipe', 'pipe'],
    env: environment,
  })
  child.stdout.on('data', (chunk) => appendJobLog(job, chunk))
  child.stderr.on('data', (chunk) => appendJobLog(job, chunk))
  child.on('error', (error) => {
    job.status = 'failed'
    job.error = error.message
    job.updatedAt = new Date().toISOString()
    activeJobId = null
    persistJob(job)
  })
  child.on('close', (code) => {
    job.status = code === 0 ? 'success' : 'failed'
    job.error = code === 0 ? null : `服务器发布进程退出码 ${code}`
    job.updatedAt = new Date().toISOString()
    activeJobId = null
    persistJob(job)
  })
  return job
}

function localOriginAllowed(request) {
  const origin = request.headers.origin
  if (!origin) return true
  try {
    const value = new URL(origin)
    return value.hostname === '127.0.0.1' || value.hostname === 'localhost'
  } catch {
    return false
  }
}

async function handleApi(request, response, pathname) {
  if (request.method === 'GET' && pathname === '/api/state') {
    const history = releases()
    const currentVersion = history[0]?.version
    const gitHead = await capture('git', ['rev-parse', '--short', 'HEAD'])
    const dirty = (await capture('git', ['status', '--porcelain'])) !== ''
    const draft = readDraft()
    sendJson(response, 200, {
      currentVersion,
      suggestedVersion: nextVersion(currentVersion),
      releases: history.slice(0, 8),
      gitHead,
      dirty,
      busy: Boolean(activeJobId),
      generating,
      draft,
      draftStale: draft?.gitHead ? draft.gitHead !== gitHead : false,
    })
    return
  }

  if (request.method === 'GET' && pathname.startsWith('/api/jobs/')) {
    const id = pathname.slice('/api/jobs/'.length)
    const job = id === 'last'
      ? [...jobs.values()].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))[0]
      : jobs.get(id)
    if (!job) {
      sendError(response, 404, '发布任务不存在')
      return
    }
    sendJson(response, 200, job)
    return
  }

  if (request.method === 'GET' && pathname === '/api/server/status') {
    try {
      const { url } = relayConfiguration()
      const healthResponse = await fetch(`${url}/health`, {
        cache: 'no-store',
        signal: AbortSignal.timeout(5000),
      })
      if (!healthResponse.ok) throw new Error(`HTTP ${healthResponse.status}`)
      const health = await healthResponse.json()
      sendJson(response, 200, {
        url,
        reachable: true,
        status: health.status || 'UNKNOWN',
        connectorOnline: health.connectorOnline === true,
        queueDepth: Number(health.queueDepth) || 0,
      })
    } catch (error) {
      sendJson(response, 200, {
        url: '',
        reachable: false,
        status: 'OFFLINE',
        connectorOnline: false,
        queueDepth: 0,
        error: error.message,
      })
    }
    return
  }

  if (pathname === '/api/draft') {
    if (!localOriginAllowed(request)) {
      sendError(response, 403, '只允许本地发布中心操作')
      return
    }
    if (request.method === 'DELETE') {
      clearDraft()
      sendJson(response, 200, { cleared: true })
      return
    }
    if (request.method === 'POST') {
      try {
        const body = await readBody(request)
        const gitHead = await capture('git', ['rev-parse', '--short', 'HEAD'])
        const draft = writeDraft(body, { gitHead })
        sendJson(response, 200, draft)
      } catch (error) {
        sendError(response, 400, error.message)
      }
      return
    }
    sendError(response, 405, '不支持的草稿操作')
    return
  }

  if (request.method !== 'POST' || !localOriginAllowed(request)) {
    sendError(response, 403, '只允许本地发布中心操作')
    return
  }

  if (pathname === '/api/server/deploy') {
    try {
      const job = startServerDeploy()
      sendJson(response, 202, { jobId: job.id })
    } catch (error) {
      sendError(response, 400, error.message)
    }
    return
  }

  if (pathname === '/api/generate') {
    if (generating) {
      sendError(response, 409, 'Codex 正在生成版本内容')
      return
    }
    generating = true
    try {
      const body = await readBody(request)
      const parsed = parseVersion(body.version)
      const history = releases()
      if (history.some((release) => release.version === parsed.version)) {
        throw new Error(`版本 ${parsed.version} 已存在`)
      }
      const preview = await runCodex(
        parsed.version,
        history[0]?.version,
        typeof body.instruction === 'string' ? body.instruction.trim() : '',
      )
      const gitHead = await capture('git', ['rev-parse', '--short', 'HEAD'])
      const draft = writeDraft({
        ...parsed,
        ...preview,
        deploy: true,
        pushTag: false,
      }, { existing: null, gitHead })
      sendJson(response, 200, draft)
    } catch (error) {
      sendError(response, 400, error.message)
    } finally {
      generating = false
    }
    return
  }

  if (pathname === '/api/publish') {
    try {
      const body = await readBody(request)
      if ((await capture('git', ['status', '--porcelain'])) !== '') {
        throw new Error('工作区有未提交改动，请先提交代码并刷新页面后再发布')
      }
      const job = startPublish(body)
      sendJson(response, 202, { jobId: job.id })
    } catch (error) {
      sendError(response, 400, error.message)
    }
    return
  }

  sendError(response, 404, '接口不存在')
}

function serveStatic(response, pathname) {
  const relative = pathname === '/' ? 'index.html' : pathname.replace(/^\/+/, '')
  const file = path.resolve(publicDirectory, relative)
  if (!(file === publicDirectory || file.startsWith(`${publicDirectory}${path.sep}`))
      || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
    response.writeHead(404)
    response.end('Not found')
    return
  }
  const extension = path.extname(file)
  const contentType = {
    '.html': 'text/html; charset=UTF-8',
    '.css': 'text/css; charset=UTF-8',
    '.js': 'text/javascript; charset=UTF-8',
    '.svg': 'image/svg+xml',
  }[extension] || 'application/octet-stream'
  const content = fs.readFileSync(file)
  response.writeHead(200, {
    'Content-Type': contentType,
    'Content-Length': content.length,
    'Cache-Control': 'no-store',
  })
  response.end(content)
}

const server = http.createServer(async (request, response) => {
  try {
    const pathname = new URL(request.url, `http://${request.headers.host}`).pathname
    if (pathname.startsWith('/api/')) {
      await handleApi(request, response, pathname)
      return
    }
    serveStatic(response, pathname)
  } catch (error) {
    if (!response.headersSent) sendError(response, 500, error.message)
    else response.end()
  }
})

server.listen(port, host, () => {
  console.log(`Regent 发布中心：http://${host}:${port}/`)
  console.log(`项目目录：${projectDirectory}`)
})
