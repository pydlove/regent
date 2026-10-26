import { createServer } from 'node:http'

const port = Number(process.env.MOCK_API_PORT ?? 8787)
const sessions = new Map()
let sequence = 0

const accounts = {
  a: {
    user: {
      id: 1,
      username: 'alice',
      displayName: '激流',
      email: 'alice@example.com',
      avatarUrl: null,
    },
    hostname: 'MacBook Pro · 书房',
    online: true,
    projects: [
      { id: 11, code: 'app_audit', name: 'app_audit', enabled: true },
      { id: 12, code: 'regent', name: 'Regent', enabled: true },
    ],
    stats: {
      PENDING: 1,
      QUEUED: 1,
      RUNNING: 2,
      BLOCKED: 1,
      DONE: 18,
      FAILED: 0,
      CANCELLED: 0,
    },
    tasks: [
      { id: 128, title: '修复登录中继偶发断开', projectName: 'app_audit' },
      { id: 127, title: '补充环境变量变更提示', projectName: 'Regent' },
    ],
    decisions: [
      {
        id: 43,
        taskId: 128,
        taskTitle: '修复登录中继偶发断开',
        projectName: 'app_audit',
        effectiveLevel: 'L2',
        question: '是否将数据库连接池上限从 20 调整为 50？',
        recommendation: '先调整为 30，并补充连接等待时间监控。',
      },
    ],
  },
  b: {
    user: {
      id: 2,
      username: 'linxia',
      displayName: '林夏',
      email: 'linxia@example.com',
      avatarUrl: null,
    },
    hostname: 'Mac mini · 工作室',
    online: false,
    projects: [{ id: 21, code: 'docs_portal', name: 'docs_portal', enabled: true }],
    stats: {
      PENDING: 1,
      QUEUED: 1,
      RUNNING: 0,
      BLOCKED: 0,
      DONE: 7,
      FAILED: 1,
      CANCELLED: 0,
    },
    tasks: [],
    decisions: [],
  },
}

function accountForUsername(username) {
  return String(username ?? '').toLowerCase().includes('b') ? accounts.b : accounts.a
}

function issue(account, installationId) {
  const id = ++sequence
  const accessToken = `mat_mock_${id}`
  const refreshToken = `mrt_mock_${id}`
  sessions.set(accessToken, { account, refreshToken })
  sessions.set(refreshToken, { account, accessToken })
  return {
    user: account.user,
    accessToken,
    accessExpiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
    refreshToken,
    refreshExpiresAt: new Date(Date.now() + 30 * 24 * 60 * 60_000).toISOString(),
    installationId,
  }
}

function response(res, status, body) {
  res.statusCode = status
  res.setHeader('Content-Type', 'application/json; charset=utf-8')
  res.end(body === undefined ? '' : JSON.stringify(body))
}

function bearer(req) {
  const value = req.headers.authorization ?? ''
  return value.startsWith('Bearer ') ? value.slice(7) : null
}

async function jsonBody(req) {
  const chunks = []
  for await (const chunk of req) chunks.push(chunk)
  if (chunks.length === 0) return {}
  return JSON.parse(Buffer.concat(chunks).toString('utf8'))
}

const server = createServer(async (req, res) => {
  try {
    const url = new URL(req.url ?? '/', `http://${req.headers.host}`)
    if (req.method === 'OPTIONS') {
      res.statusCode = 204
      res.end()
      return
    }

    if (req.method === 'POST' && url.pathname === '/api/mobile/auth/login') {
      const body = await jsonBody(req)
      response(res, 200, issue(accountForUsername(body.username), body.installationId))
      return
    }

    if (req.method === 'POST' && url.pathname === '/api/mobile/auth/refresh') {
      const body = await jsonBody(req)
      const found = sessions.get(body.refreshToken)
      if (!found?.accessToken) {
        response(res, 401, { code: 'UNAUTHORIZED', message: '登录已失效，请重新登录' })
        return
      }
      sessions.delete(found.accessToken)
      sessions.delete(body.refreshToken)
      response(res, 200, issue(found.account, 'install-mock'))
      return
    }

    const token = bearer(req)
    const session = token ? sessions.get(token) : null
    if (!session?.account) {
      response(res, 401, { code: 'UNAUTHORIZED', message: '请先登录' })
      return
    }
    const account = session.account

    if (req.method === 'POST' && url.pathname === '/api/mobile/auth/logout') {
      sessions.delete(token)
      res.statusCode = 204
      res.end()
      return
    }

    if (req.method === 'GET' && url.pathname === '/api/devices/current') {
      response(res, 200, {
        deviceId: account.online ? 'device-a' : 'device-b',
        hostname: account.hostname,
        platform: 'macOS',
        executor: 'codex-cli',
        agentInstanceId: 'agent-mock',
        online: account.online,
        codex: {
          available: account.online,
          executablePath: '/usr/local/bin/codex',
          version: '1.8.2',
          message: account.online ? 'Codex 可用' : '主机离线',
          checkedAt: new Date().toISOString(),
        },
        clientRelease: 'P0',
        shellRelease: 'P0',
        lastSeenAt: new Date(Date.now() - (account.online ? 8_000 : 540_000)).toISOString(),
        taskIds: [],
      })
      return
    }

    if (req.method === 'GET' && url.pathname === '/api/projects') {
      response(res, 200, account.projects.map((project) => ({
        ...project,
        path: `/projects/${project.code}`,
        defaultDecisionLevel: 'L2',
        summaryStatus: 'DONE',
        summary: null,
        summaryError: null,
        summaryGeneratedAt: null,
      })))
      return
    }

    if (req.method === 'GET' && url.pathname === '/api/tasks/stats') {
      response(res, 200, account.stats)
      return
    }

    if (req.method === 'GET' && url.pathname === '/api/tasks') {
      const items = account.tasks.map((task) => ({
        id: task.id,
        projectId: account.projects[0].id,
        projectName: task.projectName,
        source: 'MANUAL',
        title: task.title,
        status: 'RUNNING',
        priority: 1,
        level: 'L1',
        branchName: null,
        decisionId: null,
        attemptCount: 1,
        tokens: 1200,
        executionMillis: 80_000,
        startedAt: new Date(Date.now() - 120_000).toISOString(),
        finishedAt: null,
        createdAt: new Date(Date.now() - 180_000).toISOString(),
        updatedAt: new Date(Date.now() - 9_000).toISOString(),
      }))
      response(res, 200, {
        items,
        total: items.length,
        page: 1,
        size: items.length,
        totalPages: 1,
      })
      return
    }

    if (req.method === 'GET' && url.pathname === '/api/decisions') {
      const items = account.decisions.map((decision) => ({
        ...decision,
        projectId: account.projects[0].id,
        aiLevel: decision.effectiveLevel,
        levelEscalated: false,
        matchedRuleName: null,
        status: 'PENDING',
        options: [],
        reversible: true,
        blastRadius: '项目构建配置',
        resolvedBy: null,
        resolvedChoice: null,
        resolutionNote: null,
        raisedAt: new Date(Date.now() - 100_000).toISOString(),
        resolvedAt: null,
      }))
      response(res, 200, {
        items,
        total: items.length,
        page: 1,
        size: items.length,
        totalPages: 1,
      })
      return
    }

    response(res, 404, { code: 'NOT_FOUND', message: 'Mock API 未实现该接口' })
  } catch (error) {
    response(res, 500, {
      code: 'MOCK_ERROR',
      message: error instanceof Error ? error.message : String(error),
    })
  }
})

server.listen(port, '127.0.0.1', () => {
  console.log(`Regent mobile mock API: http://127.0.0.1:${port}`)
})
