import { get } from '@/api/http'
import type { ReleaseManifest } from '@/types/release'

/** 公开的发布清单；没有 latest.json 时后端返回空清单而不是 404。 */
export const getLatestRelease = () => get<ReleaseManifest>('/release/latest')
