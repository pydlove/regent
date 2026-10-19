import releaseData from './releases.json' with { type: 'json' }

export type ReleaseHighlight = {
  title: string
  description: string
}

export type AppRelease = {
  version: string
  label: string
  releasedAt: string
  summary: string
  highlights: ReleaseHighlight[]
}

/** 最新版本永远排在第一位；发布脚本会把新版本插入数组头部。 */
export const RELEASES: readonly AppRelease[] = releaseData.releases

export const CURRENT_RELEASE = RELEASES[0]

export const APP_VERSION = CURRENT_RELEASE.version
