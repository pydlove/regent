export type DownloadPlatformId = 'macos' | 'windows'

export function isWindowsClient(userAgent: string | null | undefined): boolean {
  return /Windows(?: NT|;)/i.test(userAgent ?? '')
}

export function preferredDownloadOrder(windows: boolean): DownloadPlatformId[] {
  return windows ? ['windows', 'macos'] : ['macos', 'windows']
}

export function recommendedDownloadPlatform(windows: boolean): DownloadPlatformId {
  return windows ? 'windows' : 'macos'
}
