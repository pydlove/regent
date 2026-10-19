export const AUTH_RECOVERY_MAX_ATTEMPTS = 30
export const AUTH_RECOVERY_DELAY_MS = 2000

function readStatus(error: unknown): number | null {
  if (typeof error !== 'object' || error === null || !('status' in error)) return null
  const status = (error as { status?: unknown }).status
  return typeof status === 'number' ? status : null
}

export function shouldRetryAuth(error: unknown): boolean {
  const status = readStatus(error)
  // 没有 HTTP 状态通常意味着连接未建立、被重置或请求超时，属于可恢复中断。
  return status === null || status === 0 || status >= 500
}
