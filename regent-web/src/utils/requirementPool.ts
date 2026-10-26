import type { RequirementPoolStatus } from '@/types/api'

export type RequirementDisplayStatus =
  | 'PENDING_VERSION'
  | 'PENDING_REVIEW'
  | 'IN_REVIEW'
  | 'FORMAL'
  | 'REJECTED'

type RequirementPoolState = {
  poolStatus: RequirementPoolStatus
  versionId: number | null
}

export function formatRequirementNo(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value) || value <= 0) {
    return '—'
  }
  return String(Math.trunc(value)).padStart(6, '0')
}

export function requirementDisplayStatus(
  requirement: RequirementPoolState,
): RequirementDisplayStatus {
  if (requirement.poolStatus === 'POOL') {
    return requirement.versionId === null ? 'PENDING_VERSION' : 'PENDING_REVIEW'
  }
  return requirement.poolStatus
}
