const rawBase = import.meta.env.BASE_URL || '/'

/** 部署在域名子路径时去掉尾部斜杠；根路径部署时保持空串。 */
export const APP_BASE_PATH = rawBase === '/'
  ? ''
  : `/${rawBase.replace(/^\/+|\/+$/g, '')}`

export function appPath(path: string): string {
  const normalized = path.startsWith('/') ? path : `/${path}`
  return `${APP_BASE_PATH}${normalized}` || '/'
}

export function appServerUrl(): string {
  return `${window.location.origin}${APP_BASE_PATH}` || window.location.origin
}
