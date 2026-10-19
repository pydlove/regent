import { deleteRequest, get, putJson } from '@/api/http'
import type { CurrentTaskDraft, TaskDraft } from '@/types/api'

export type SaveTaskDraftInput = {
  projectId: number | null
  prompt: string
  priority: number
  pluginIds: number[]
}

export const getCurrentTaskDraft = () =>
  get<CurrentTaskDraft>('/task-drafts/current')

export const saveCurrentTaskDraft = (input: SaveTaskDraftInput) =>
  putJson<TaskDraft>('/task-drafts/current', input)

export const deleteCurrentTaskDraft = () =>
  deleteRequest('/task-drafts/current')
