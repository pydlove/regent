import type { ApiErrorBody } from '@/types/api'

/*
 * 唯一的 HTTP 出口。所有请求都从这里走，好处是错误处理只有一份：
 * 后端 400 时返回的是 {code, message}，界面要展示的正是 message ——
 * 如果每个调用点自己 fetch，就得各自记得解析这个结构，
 * 迟早会出现「某个页面弹出了 [object Object]」。
 */

export class ApiError extends Error {
  readonly code: string
  readonly status: number

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as ApiErrorBody
    return new ApiError(response.status, body.code, body.message)
  } catch {
    // 后端没按约定返回 JSON（网关 502、连接被重置等），给一句人话而不是 parse 异常的堆栈
    return new ApiError(response.status, 'UNKNOWN', `请求失败（HTTP ${response.status}）`)
  }
}

type QueryValue = string | number | undefined | null

function withQuery(path: string, params?: Record<string, QueryValue>): string {
  if (!params) return `/api${path}`
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  }
  const query = search.toString()
  return query ? `/api${path}?${query}` : `/api${path}`
}

export async function get<T>(path: string, params?: Record<string, QueryValue>): Promise<T> {
  const response = await fetch(withQuery(path, params), { headers: { Accept: 'application/json' } })
  if (!response.ok) throw await toApiError(response)
  return (await response.json()) as T
}

export async function post<T>(path: string): Promise<T> {
  const response = await fetch(`/api${path}`, {
    method: 'POST',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await toApiError(response)
  return (await response.json()) as T
}

/**
 * 带 JSON 请求体的 POST。
 *
 * <p>与不带请求体的 {@link post} 分成两个函数，而不是把 body 做成可选参数：
 * 申请体为 `undefined` 与「显式提交一个空对象」在服务端是两种不同的语义
 * （后者会覆盖默认值），合成一个参数后调用点很容易在两者之间飘。
 */
export async function postJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`/api${path}`, {
    method: 'POST',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify(body ?? {}),
  })
  if (!response.ok) throw await toApiError(response)
  return (await response.json()) as T
}
