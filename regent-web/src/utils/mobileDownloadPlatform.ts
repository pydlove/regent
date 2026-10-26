export type MobileDownloadPlatform = 'ios' | 'android' | 'other'
export type DesktopOperatingSystem = 'macos' | 'windows' | 'other'

/**
 * iPadOS 默认发送桌面版 Safari UA，只能结合触点数识别。
 * 该结果只用于排序和推荐，不会隐藏另一种平台的下载入口。
 */
export function detectMobileDownloadPlatform(
  userAgent: string,
  maxTouchPoints = 0,
): MobileDownloadPlatform {
  if (/android/i.test(userAgent)) return 'android'
  if (/iphone|ipad|ipod/i.test(userAgent)) return 'ios'
  if (/macintosh/i.test(userAgent) && maxTouchPoints > 1) return 'ios'
  return 'other'
}

/** iPadOS 伪装成 Mac 时必须排除，避免把平板误判成 Mac 桌面。 */
export function detectDesktopOperatingSystem(
  userAgent: string,
  maxTouchPoints = 0,
): DesktopOperatingSystem {
  if (/iphone|ipad|ipod/i.test(userAgent)) return 'other'
  if (/macintosh|mac os x/i.test(userAgent) && maxTouchPoints <= 1) return 'macos'
  if (/windows/i.test(userAgent)) return 'windows'
  return 'other'
}
