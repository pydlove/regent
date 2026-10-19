import { ref } from 'vue'

import {
  deleteCurrentTaskDraft,
  getCurrentTaskDraft,
  saveCurrentTaskDraft,
} from '@/api/taskDrafts'
import type { TaskDraft } from '@/types/api'
import {
  normalizeTaskDraftFields,
  taskDraftHasContent,
  taskDraftAutosaveDelay,
  type TaskDraftFields,
} from '@/utils/taskDraft'

export type TaskDraftSaveState = 'idle' | 'pending' | 'saving' | 'saved' | 'error'

const errorMessage = (error: unknown) =>
  error instanceof Error ? error.message : '草稿自动保存失败'

/**
 * 单份当前草稿的自动保存器。
 *
 * <p>保存请求串行执行；请求返回前又有新输入时，只把最新快照补存一次，
 * 避免较慢的旧请求最后落库、覆盖用户刚写的内容。
 */
export function useTaskDraftAutosave() {
  const draft = ref<TaskDraft | null>(null)
  const loading = ref(false)
  const state = ref<TaskDraftSaveState>('idle')
  const error = ref<string | null>(null)
  const savedAt = ref<string | null>(null)

  let timer: ReturnType<typeof setTimeout> | null = null
  let requested: TaskDraftFields | null = null
  let requestedRevision = 0
  let running: Promise<void> | null = null
  let suspended = false
  let firstChangeAt: number | null = null
  let loadSequence = 0

  const clearTimer = () => {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  }

  const armTimer = () => {
    if (requested === null || suspended) return
    clearTimer()
    if (firstChangeAt === null) firstChangeAt = Date.now()
    timer = setTimeout(() => {
      timer = null
      void drain()
    }, taskDraftAutosaveDelay(firstChangeAt, Date.now()))
  }

  const load = async () => {
    const sequence = ++loadSequence
    if (running) await running
    if (sequence !== loadSequence) return draft.value
    clearTimer()
    requested = null
    firstChangeAt = null
    draft.value = null
    savedAt.value = null
    loading.value = true
    error.value = null
    try {
      const loaded = (await getCurrentTaskDraft()).draft
      if (sequence !== loadSequence) return draft.value
      draft.value = loaded
      savedAt.value = draft.value?.updatedAt ?? null
      state.value = draft.value ? 'saved' : 'idle'
      return draft.value
    } catch (e) {
      if (sequence !== loadSequence) return draft.value
      error.value = errorMessage(e)
      state.value = 'error'
      return null
    } finally {
      if (sequence === loadSequence) loading.value = false
    }
  }

  const drain = async () => {
    if (running) return running
    running = (async () => {
      while (requested && !suspended) {
        clearTimer()
        firstChangeAt = null
        const revision = requestedRevision
        const payload = requested
        requested = null
        state.value = 'saving'
        error.value = null
        try {
          draft.value = await saveCurrentTaskDraft(payload)
          savedAt.value = draft.value.updatedAt
          if (revision === requestedRevision) state.value = 'saved'
        } catch (e) {
          if (revision === requestedRevision) {
            error.value = errorMessage(e)
            state.value = 'error'
          }
        }
      }
    })()
    try {
      await running
    } finally {
      running = null
    }
  }

  const setRequested = (fields: TaskDraftFields) => {
    requested = normalizeTaskDraftFields(fields)
    requestedRevision++
  }

  const schedule = (fields: TaskDraftFields) => {
    if (suspended) return
    const normalized = normalizeTaskDraftFields(fields)
    if (!taskDraftHasContent(normalized, draft.value !== null, running !== null)) {
      clearTimer()
      requested = null
      firstChangeAt = null
      state.value = 'idle'
      return
    }
    if (firstChangeAt === null) firstChangeAt = Date.now()
    setRequested(normalized)
    state.value = 'pending'
    error.value = null
    armTimer()
  }

  const flush = async (fields: TaskDraftFields) => {
    if (suspended) return
    clearTimer()
    const normalized = normalizeTaskDraftFields(fields)
    if (!taskDraftHasContent(normalized, draft.value !== null, running !== null)) return
    firstChangeAt = null
    setRequested(normalized)
    await drain()
  }

  const remove = async () => {
    loadSequence++
    suspended = true
    clearTimer()
    requested = null
    firstChangeAt = null
    if (running) await running
    await deleteCurrentTaskDraft()
    draft.value = null
    savedAt.value = null
    state.value = 'idle'
    error.value = null
  }

  const resume = () => {
    suspended = false
    if (requested !== null) armTimer()
  }

  const suspend = () => {
    suspended = true
    clearTimer()
  }

  return {
    draft,
    loading,
    state,
    error,
    savedAt,
    load,
    schedule,
    flush,
    remove,
    suspend,
    resume,
  }
}
