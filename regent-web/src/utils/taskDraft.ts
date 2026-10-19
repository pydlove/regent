export type TaskDraftFields = {
  projectId: number | null
  prompt: string
  priority: number
  pluginIds: number[]
}

export const TASK_DRAFT_AUTOSAVE_DELAY_MS = 600
export const TASK_DRAFT_AUTOSAVE_MAX_WAIT_MS = 3_000

/** 请求边界统一收口，避免空白输入、NaN 与重复插件被自动保存带进服务端。 */
export const normalizeTaskDraftFields = (fields: TaskDraftFields): TaskDraftFields => ({
  projectId: fields.projectId,
  prompt: fields.prompt ?? '',
  priority: Number.isFinite(fields.priority) ? Math.trunc(fields.priority) : 0,
  pluginIds: [...new Set(fields.pluginIds)],
})

/**
 * 空表单本身不值得创建草稿。已有草稿后即使被清空也要继续保存，
 * 这样用户是在主动删内容，而不是关掉弹框后又被旧值恢复。
 *
 * <p>第一次保存还在路上时也算已有草稿：用户若在这段时间里清空输入，
 * 必须继续补存空快照，否则服务端会留下旧正文。
 */
export const taskDraftHasContent = (
  fields: TaskDraftFields,
  hasExistingDraft: boolean,
  hasInFlightSave = false,
) =>
  hasExistingDraft ||
  hasInFlightSave ||
  fields.prompt.trim().length > 0 ||
  fields.pluginIds.length > 0

/** 连续输入不断重置防抖计时，但仍保证第一次改动后最多等待三秒就保存。 */
export const taskDraftAutosaveDelay = (
  firstChangeAt: number,
  now: number,
  debounceMs = TASK_DRAFT_AUTOSAVE_DELAY_MS,
  maxWaitMs = TASK_DRAFT_AUTOSAVE_MAX_WAIT_MS,
) => {
  const elapsed = Math.max(0, now - firstChangeAt)
  return Math.max(0, Math.min(debounceMs, maxWaitMs - elapsed))
}
