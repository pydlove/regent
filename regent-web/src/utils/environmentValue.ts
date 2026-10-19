export const ENVIRONMENT_NAME_MAX_LENGTH = 64
export const MASKED_ENVIRONMENT_VALUE = '••••••••'

type EnvironmentValue = {
  value: string
  secret: boolean
}

function parseUrl(value: string): URL | null {
  try {
    return new URL(value.trim())
  } catch {
    return null
  }
}

export function isHttpUrl(value: string): boolean {
  const url = parseUrl(value)
  return url !== null && (url.protocol === 'http:' || url.protocol === 'https:')
}

export function hasInlineUrlCredentials(value: string): boolean {
  const url = parseUrl(value)
  if (url !== null && (url.username !== '' || url.password !== '')) {
    return true
  }
  // JDBC 连串不会被 WHATWG URL 拆出 userinfo，需要单独兜住 jdbc:mysql://user:pass@host。
  return /^(?:[a-z][a-z0-9+.-]*:)+\/\/[^/\s@]+:[^/\s@]+@/i.test(value.trim())
}

/** 显式标记的密码值，以及带账号密码的 URL，都不允许在列表里直接露出。 */
export function isMaskedEnvironmentValue(entry: EnvironmentValue): boolean {
  return entry.secret || hasInlineUrlCredentials(entry.value)
}
