export const CC_SWITCH_SITE_URL = 'https://ccswitch.io/zh/'
export const CC_SWITCH_RELEASES_URL =
  'https://github.com/farion1231/cc-switch/releases/latest'

const RELEASE_API_URL =
  'https://api.github.com/repos/farion1231/cc-switch/releases/latest'
const CACHE_TTL_MS = 10 * 60 * 1000

export type CcSwitchAsset = {
  name: string
  url: string
  sizeBytes: number
}

export type CcSwitchRelease = {
  version: string
  publishedAt: string | null
  releaseUrl: string
  assets: {
    macos: CcSwitchAsset | null
    windowsInstaller: CcSwitchAsset | null
    windowsPortable: CcSwitchAsset | null
    linuxAppImage: CcSwitchAsset | null
  }
}

type GithubAsset = {
  name?: unknown
  browser_download_url?: unknown
  size?: unknown
}

type GithubRelease = {
  tag_name?: unknown
  html_url?: unknown
  published_at?: unknown
  assets?: unknown
}

function asAsset(value: GithubAsset | undefined): CcSwitchAsset | null {
  if (
    !value ||
    typeof value.name !== 'string' ||
    typeof value.browser_download_url !== 'string'
  ) {
    return null
  }
  return {
    name: value.name,
    url: value.browser_download_url,
    sizeBytes: typeof value.size === 'number' ? value.size : 0,
  }
}

export function parseCcSwitchRelease(value: GithubRelease): CcSwitchRelease {
  const assets = Array.isArray(value.assets)
    ? value.assets.filter((asset): asset is GithubAsset => typeof asset === 'object' && asset !== null)
    : []
  const find = (pattern: RegExp) =>
    asAsset(assets.find((asset) => typeof asset.name === 'string' && pattern.test(asset.name)))

  return {
    version: typeof value.tag_name === 'string'
      ? value.tag_name.replace(/^v/i, '')
      : '最新版本',
    publishedAt: typeof value.published_at === 'string' ? value.published_at : null,
    releaseUrl: typeof value.html_url === 'string' ? value.html_url : CC_SWITCH_RELEASES_URL,
    assets: {
      macos: find(/-macOS\.dmg$/i),
      windowsInstaller: find(/-Windows\.msi$/i),
      windowsPortable: find(/-Windows-Portable\.zip$/i),
      linuxAppImage: find(/-Linux-x86_64\.AppImage$/i),
    },
  }
}

export type CcSwitchPlatform = 'macos' | 'windows' | 'linux'

export function detectCcSwitchPlatform(userAgent: string | null | undefined): CcSwitchPlatform {
  const value = userAgent ?? ''
  if (/Windows/i.test(value)) return 'windows'
  if (/Linux/i.test(value) && !/Android/i.test(value)) return 'linux'
  return 'macos'
}

let cachedRelease: { value: CcSwitchRelease; expiresAt: number } | null = null
let loadingRelease: Promise<CcSwitchRelease> | null = null

export async function getCcSwitchRelease(force = false): Promise<CcSwitchRelease> {
  if (!force && cachedRelease && cachedRelease.expiresAt > Date.now()) {
    return cachedRelease.value
  }
  if (!force && loadingRelease) return loadingRelease

  const controller = new AbortController()
  const timer = globalThis.setTimeout(() => controller.abort(), 8000)
  loadingRelease = fetch(RELEASE_API_URL, {
    headers: {
      Accept: 'application/vnd.github+json',
    },
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) throw new Error(`GitHub 返回 HTTP ${response.status}`)
      const release = parseCcSwitchRelease(await response.json() as GithubRelease)
      cachedRelease = {
        value: release,
        expiresAt: Date.now() + CACHE_TTL_MS,
      }
      return release
    })
    .finally(() => {
      globalThis.clearTimeout(timer)
      loadingRelease = null
    })
  return loadingRelease
}
