import type { ApiErrorBody } from '@/types/api'

// Node 原生测试也会直接加载这个文件，不能在模块顶层假设 Vite 的 import.meta.env 一定存在。
const rawBase = (import.meta as ImportMeta & { env?: { BASE_URL?: string } }).env?.BASE_URL || '/'
const API_BASE_PATH = rawBase === '/'
  ? ''
  : `/${rawBase.replace(/^\/+|\/+$/g, '')}`

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

const DEFAULT_TIMEOUT_MS = 15_000
const UPLOAD_TIMEOUT_MS = 60_000

export type DownloadedFile = {
  blob: Blob
  filename: string | null
  headers: Headers
}

/**
 * 所有 JSON 请求都要有明确的墙钟上限。
 *
 * <p>浏览器默认不会给 fetch 设超时；代理断开、后端重启或连接半开时，请求可能一直挂着，
 * 页面就会永远停在骨架屏。超时以后转成 ApiError，调用方才有机会展示失败态和重试入口。
 */
async function fetchWithTimeout(
  input: RequestInfo | URL,
  init: RequestInit,
  timeoutMs = DEFAULT_TIMEOUT_MS,
): Promise<Response> {
  const controller = new AbortController()
  const timer = globalThis.setTimeout(() => controller.abort(), timeoutMs)
  try {
    return await fetch(input, { ...init, signal: controller.signal })
  } catch (error) {
    if (controller.signal.aborted) {
      throw new ApiError(0, 'TIMEOUT', `请求超时（${Math.round(timeoutMs / 1000)} 秒），请稍后重试`)
    }
    throw error
  } finally {
    globalThis.clearTimeout(timer)
  }
}

/*
 * 公网中继用这个自定义头拦跨站表单写请求。
 * 同源请求不会触发预检；跨站表单又无法设置自定义头，因此中继可以把它当作轻量 CSRF 标记。
 */
const RELAY_REQUEST_HEADER = { 'X-Regent-Relay-Request': '1' }

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as ApiErrorBody
    return new ApiError(response.status, body.code, body.message)
  } catch {
    // 后端没按约定返回 JSON（网关 502、连接被重置等），给一句人话而不是 parse 异常的堆栈
    return new ApiError(response.status, 'UNKNOWN', `请求失败（HTTP ${response.status}）`)
  }
}

export function filenameFromContentDisposition(value: string | null): string | null {
  if (!value) return null
  const encoded = /filename\*\s*=\s*([^;]+)/i.exec(value)?.[1]?.trim()
  if (encoded) {
    const payload = encoded.replace(/^["']|["']$/g, '')
    const separator = payload.indexOf("''")
    const raw = separator >= 0 ? payload.slice(separator + 2) : payload
    try {
      return decodeURIComponent(raw)
    } catch {
      return raw
    }
  }
  const plain = /filename\s*=\s*(?:"([^"]*)"|([^;]*))/i.exec(value)
  return (plain?.[1] ?? plain?.[2] ?? '').trim() || null
}

async function toDownloadedFile(
  response: Response,
  fallbackFilename: string,
): Promise<DownloadedFile> {
  const filename = filenameFromContentDisposition(
    response.headers.get('Content-Disposition'),
  ) ?? fallbackFilename
  return {
    blob: await response.blob(),
    filename,
    headers: response.headers,
  }
}

async function throwApiError(path: string, response: Response): Promise<never> {
  const error = await toApiError(response)
  if (
    error.status === 401 &&
    !path.startsWith('/auth/') &&
    typeof window !== 'undefined'
  ) {
    window.dispatchEvent(new Event('regent:unauthorized'))
  }
  throw error
}

type QueryValue = string | number | undefined | null

function withQuery(path: string, params?: Record<string, QueryValue>): string {
  if (!params) return apiUrl(path)
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  }
  const query = search.toString()
  return query ? `${apiUrl(path)}?${query}` : apiUrl(path)
}

export function apiUrl(path: string): string {
  const normalized = path.startsWith('/') ? path : `/${path}`
  return `${API_BASE_PATH}/api${normalized}`
}

export function resolveApiResourceUrl(
  value: string | null | undefined,
  apiBasePath: string,
): string | null {
  if (!value) return value ?? null
  if (/^(?:[a-z][a-z\d+.-]*:)?\/\//i.test(value) || /^(?:data|blob):/i.test(value)) {
    return value
  }
  const apiPath = value.startsWith('/api/')
    ? value.slice(4)
    : value.startsWith('api/')
      ? `/${value.slice(4)}`
      : value
  const normalized = apiPath.startsWith('/') ? apiPath : `/${apiPath}`
  return `${apiBasePath}/api${normalized}`
}

export function apiResourceUrl(path: string): string {
  return resolveApiResourceUrl(path, API_BASE_PATH) ?? ''
}

export async function get<T>(path: string, params?: Record<string, QueryValue>): Promise<T> {
  const response = await fetchWithTimeout(withQuery(path, params), {
    headers: { ...RELAY_REQUEST_HEADER, Accept: 'application/json' },
  })
  if (!response.ok) await throwApiError(path, response)
  return (await response.json()) as T
}

export async function post<T>(path: string): Promise<T> {
  const response = await fetchWithTimeout(apiUrl(path), {
    method: 'POST',
    headers: { ...RELAY_REQUEST_HEADER, Accept: 'application/json' },
  })
  if (!response.ok) await throwApiError(path, response)
  return (await response.json()) as T
}

/** 无响应体的 POST。用于登出这类只改变 Cookie、没有业务数据返回的命令。 */
export async function postNoContent(path: string): Promise<void> {
  const response = await fetchWithTimeout(apiUrl(path), {
    method: 'POST',
    headers: { ...RELAY_REQUEST_HEADER, Accept: 'application/json' },
  })
  if (!response.ok) await throwApiError(path, response)
}

/**
 * 带 JSON 请求体的 POST。
 *
 * <p>与不带请求体的 {@link post} 分成两个函数，而不是把 body 做成可选参数：
 * 申请体为 `undefined` 与「显式提交一个空对象」在服务端是两种不同的语义
 * （后者会覆盖默认值），合成一个参数后调用点很容易在两者之间飘。
 */
export async function postJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetchWithTimeout(apiUrl(path), {
    method: 'POST',
    headers: {
      ...RELAY_REQUEST_HEADER,
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body ?? {}),
  })
  if (!response.ok) await throwApiError(path, response)
  return (await response.json()) as T
}

/** 长耗时 JSON POST：一键安装这类操作需要比普通请求更长的墙钟上限。 */
export async function postJsonWithTimeout<T>(
  path: string,
  body: unknown,
  timeoutMs: number,
): Promise<T> {
  const response = await fetchWithTimeout(apiUrl(path), {
    method: 'POST',
    headers: {
      ...RELAY_REQUEST_HEADER,
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body ?? {}),
  }, timeoutMs)
  if (!response.ok) await throwApiError(path, response)
  return (await response.json()) as T
}

/** 带 JSON 请求体、但成功响应没有响应体的 POST。 */
export async function postJsonNoContent(path: string, body: unknown): Promise<void> {
  const response = await fetchWithTimeout(apiUrl(path), {
    method: 'POST',
    headers: {
      ...RELAY_REQUEST_HEADER,
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body ?? {}),
  })
  if (!response.ok) await throwApiError(path, response)
}

/** Multipart 上传。Content-Type 必须交给浏览器生成，才能带上 boundary。 */
export async function postForm<T>(path: string, body: FormData): Promise<T> {
  const response = await fetchWithTimeout(apiUrl(path), {
    method: 'POST',
    headers: {
      ...RELAY_REQUEST_HEADER,
      Accept: 'application/json',
    },
    body,
  }, UPLOAD_TIMEOUT_MS)
  if (!response.ok) await throwApiError(path, response)
  return (await response.json()) as T
}

export async function getFile(
  path: string,
  fallbackFilename: string,
  timeoutMs = DEFAULT_TIMEOUT_MS,
): Promise<DownloadedFile> {
  const response = await fetchWithTimeout(apiUrl(path), {
    headers: { ...RELAY_REQUEST_HEADER, Accept: 'application/octet-stream' },
  }, timeoutMs)
  if (!response.ok) await throwApiError(path, response)
  return toDownloadedFile(response, fallbackFilename)
}

export async function postFormFile(
  path: string,
  body: FormData,
  fallbackFilename: string,
  timeoutMs = UPLOAD_TIMEOUT_MS,
): Promise<DownloadedFile> {
  const response = await fetchWithTimeout(apiUrl(path), {
    method: 'POST',
    headers: { ...RELAY_REQUEST_HEADER, Accept: 'application/octet-stream' },
    body,
  }, timeoutMs)
  if (!response.ok) await throwApiError(path, response)
  return toDownloadedFile(response, fallbackFilename)
}

/** 带 JSON 请求体的 PUT：用于整体替换一组可编辑字段。 */
export async function putJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetchWithTimeout(apiUrl(path), {
    method: 'PUT',
    headers: {
      ...RELAY_REQUEST_HEADER,
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body ?? {}),
  })
  if (!response.ok) await throwApiError(path, response)
  return (await response.json()) as T
}

/** 带 JSON 请求体的 PATCH：用于局部状态迁移。 */
export async function patchJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetchWithTimeout(apiUrl(path), {
    method: 'PATCH',
    headers: {
      ...RELAY_REQUEST_HEADER,
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body ?? {}),
  })
  if (!response.ok) await throwApiError(path, response)
  return (await response.json()) as T
}

/** 无响应体的 DELETE。204 是删除接口的正常返回，不能按 JSON 解析。 */
export async function deleteRequest(path: string): Promise<void> {
  const response = await fetchWithTimeout(apiUrl(path), {
    method: 'DELETE',
    headers: { ...RELAY_REQUEST_HEADER, Accept: 'application/json' },
  })
  if (!response.ok) await throwApiError(path, response)
}
