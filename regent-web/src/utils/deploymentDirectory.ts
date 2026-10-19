type DirectoryCandidate = {
  value: string
  score: number
  order: number
}

const ASSIGNMENT_PATTERN =
  /^\s*(?:(?:export|readonly|local)\s+|declare(?:\s+-[A-Za-z]+)*\s+)*([A-Za-z_][A-Za-z0-9_]*)=(?:"([^"]*)"|'([^']*)'|([^\s;#]+))/
const CHANGE_DIRECTORY_PATTERN =
  /^\s*(?:cd|pushd)\s+(?:--\s+)?(?:"([^"]+)"|'([^']+)'|([^\s;&|]+))/
const HEREDOC_PATTERN = /<<-?\s*(['"]?)([A-Za-z_][A-Za-z0-9_]*)\1/

const PRIMARY_DIRECTORY_VARIABLES: Record<string, number> = {
  REMOTE_DIR: 130,
  REMOTE_APP_DIR: 125,
  REMOTE_BASE: 120,
  DEPLOY_DIR: 115,
  DEPLOY_ROOT: 115,
  DEPLOYMENT_DIR: 115,
  APP_DIR: 110,
  APP_ROOT: 105,
  TARGET_DIR: 100,
  INSTALL_DIR: 95,
  SERVICE_DIR: 90,
  WORK_DIR: 85,
  DEST_DIR: 80,
  RELEASE_DIR: 80,
}

/**
 * 从常见 shell 目录变量或 cd 语句中推断部署目录。
 *
 * 只接受可静态确定的绝对路径或 ~ 路径；引用 $REGENT_DEPLOY_DIRECTORY、命令替换和
 * 未定义变量的写法不会被猜测。
 */
export function inferDeploymentDirectory(script: string | null | undefined): string | null {
  if (!script?.trim()) return null

  const variables = new Map<string, string>()
  const candidates: DirectoryCandidate[] = []
  const lines = script.replace(/\r\n?/g, '\n').replace(/\\\n/g, ' ').split('\n')
  let heredocEnd: string | null = null
  let order = 0

  for (const line of lines) {
    if (heredocEnd) {
      if (line.trim() === heredocEnd) heredocEnd = null
      continue
    }

    const assignment = line.match(ASSIGNMENT_PATTERN)
    if (assignment) {
      const name = assignment[1]
      const rawValue = assignment[2] ?? assignment[3] ?? assignment[4] ?? ''
      const value = resolveDirectory(rawValue, variables)
      if (value) variables.set(name, value)

      const score = variableScore(name)
      const totalScore = value ? score + pathScore(value) : 0
      if (value && totalScore > 0) {
        candidates.push({ value, score: totalScore, order: order++ })
      }
    }

    const changedDirectory = line.match(CHANGE_DIRECTORY_PATTERN)
    if (changedDirectory) {
      const rawValue = changedDirectory[1] ?? changedDirectory[2] ?? changedDirectory[3] ?? ''
      const value = resolveDirectory(rawValue, variables)
      const score = value ? 70 + pathScore(value) : 0
      if (value && score > 0) candidates.push({ value, score, order: order++ })
    }

    const heredoc = line.match(HEREDOC_PATTERN)
    if (heredoc) heredocEnd = heredoc[2]
  }

  candidates.sort((left, right) => right.score - left.score || right.order - left.order)
  return candidates[0]?.value ?? null
}

function variableScore(name: string): number {
  const normalized = name.toUpperCase()
  if (
    /(^|_)(LOCAL|SCRIPT|SOURCE|BUILD|TMP|TEMP|CACHE|BACKUP|LOG|CONF|CONFIG|THIRDPARTY|ASSET|RESOURCE)(_|$)/.test(
      normalized,
    )
  ) {
    return -1
  }
  if (PRIMARY_DIRECTORY_VARIABLES[normalized]) {
    return PRIMARY_DIRECTORY_VARIABLES[normalized]
  }
  if (/_DIR$/.test(normalized)) return normalized.includes('REMOTE') ? 100 : 60
  if (/(?:_ROOT|_BASE)$/.test(normalized)) return 55
  return -1
}

function pathScore(path: string): number {
  if (/^\/(?:Users|Volumes)\//.test(path) || path.startsWith('/private/var/folders/')) {
    return -1000
  }
  if (/^\/(?:tmp|var\/tmp)(?:\/|$)/.test(path)) return -1000
  return 0
}

function resolveDirectory(rawValue: string, variables: Map<string, string>): string | null {
  let value = rawValue.trim()
  if (!value || value.includes('$(') || value.includes('`')) return null

  value = value.replace(/\$(?:\{HOME\}|HOME\b)/g, '~')
  for (let pass = 0; pass < 8; pass++) {
    let unresolved = false
    const expanded = value.replace(
      /\$\{([A-Za-z_][A-Za-z0-9_]*)\}|\$([A-Za-z_][A-Za-z0-9_]*)/g,
      (match, bracedName: string | undefined, plainName: string | undefined) => {
        const name = bracedName ?? plainName ?? ''
        const replacement = variables.get(name)
        if (replacement === undefined) {
          unresolved = true
          return match
        }
        return replacement
      },
    )
    if (unresolved) return null
    if (expanded === value) break
    value = expanded
  }

  if (value === '~') return value
  if (value.startsWith('~/')) {
    return value.length > 2 ? value.replace(/\/+$/, '') : '~'
  }
  if (!value.startsWith('/')) return null
  return value === '/' ? value : value.replace(/\/+$/, '')
}
