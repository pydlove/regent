const EXPLICIT_SCHEME = /^[a-z][a-z0-9+.-]*:\/\//i
const HOST_PATTERN = /^[a-z0-9.-]+$/i
const PAGE_ROOTS = new Set([
  'dashboard',
  'tasks',
  'decisions',
  'projects',
  'environments',
  'bugs',
  'settings',
])

export type RelayServerUrlInspection = {
  valid: boolean
  normalized: string
  pagePath: string
}

function isIpv4(value: string): boolean {
  const parts = value.split('.')
  return parts.length === 4
    && parts.every((part) => /^\d{1,3}$/.test(part) && Number(part) <= 255)
}

export function isRelayServerHost(rawValue: string): boolean {
  const value = rawValue.trim()
  if (!value || value.length > 255 || !HOST_PATTERN.test(value)) return false
  if (value.startsWith('.') || value.endsWith('.') || value.includes('..')) return false
  if (/^\d+(?:\.\d+){3}$/.test(value) && !isIpv4(value)) return false
  return value.split('.').every(
    (label) => label.length <= 63
      && /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/i.test(label),
  )
}

function defaultScheme(rawValue: string): string {
  const host = rawValue
    .replace(/^\/+/, '')
    .split(/[/:?#]/, 1)[0]
  return isIpv4(host) ? 'http' : 'https'
}

function urlCandidate(rawValue: string): string {
  if (EXPLICIT_SCHEME.test(rawValue)) return rawValue
  if (rawValue.startsWith('//')) return `https:${rawValue}`
  return `${defaultScheme(rawValue)}://${rawValue}`
}

function siteRoot(pathname: string): { sitePath: string; pagePath: string } {
  const segments = pathname.split('/').filter(Boolean)
  let pageIndex = -1
  for (let index = 0; index < segments.length; index += 1) {
    if (PAGE_ROOTS.has(segments[index].toLowerCase())) {
      pageIndex = index
      break
    }
  }
  const baseSegments = pageIndex >= 0 ? segments.slice(0, pageIndex) : segments
  const sitePath = baseSegments.length > 0 ? `/${baseSegments.join('/')}/` : '/'
  const pagePath = pageIndex >= 0
    ? `/${segments.slice(pageIndex).join('/')}`
    : ''
  return { sitePath, pagePath }
}

export function inspectRelayServerUrl(
  rawValue: string,
  monitoringEnabled: boolean,
): RelayServerUrlInspection {
  const raw = rawValue.trim()
  if (!raw) {
    return {
      valid: !monitoringEnabled,
      normalized: '',
      pagePath: '',
    }
  }
  if (/\s/.test(raw)) {
    return { valid: false, normalized: '', pagePath: '' }
  }
  try {
    const url = new URL(urlCandidate(raw))
    if (
      (url.protocol !== 'http:' && url.protocol !== 'https:')
      || !isRelayServerHost(url.hostname)
      || url.username
      || url.password
      || url.search
      || url.hash
    ) {
      return { valid: false, normalized: '', pagePath: '' }
    }
    const root = siteRoot(url.pathname)
    return {
      valid: true,
      normalized: `${url.origin}${root.sitePath}`,
      pagePath: root.pagePath,
    }
  } catch {
    return { valid: false, normalized: '', pagePath: '' }
  }
}

export function defaultRelayServerUrl(rawHost: string): string {
  const host = rawHost.trim()
  return `${defaultScheme(host)}://${host}${isIpv4(host) ? ':18787' : '/'}`
}
