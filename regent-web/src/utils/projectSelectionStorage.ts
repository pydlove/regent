export const PROJECT_SELECTION_STORAGE_KEY = 'regent-project-id'

type ProjectSelectionStorage = Pick<Storage, 'getItem' | 'removeItem' | 'setItem'>

function browserStorage(): ProjectSelectionStorage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

export function readProjectSelection(
  storage: ProjectSelectionStorage | null = browserStorage(),
): number | null {
  if (!storage) return null

  try {
    const raw = storage.getItem(PROJECT_SELECTION_STORAGE_KEY)
    if (raw === null) return null

    const parsed = Number(raw)
    if (Number.isSafeInteger(parsed) && parsed > 0) return parsed

    storage.removeItem(PROJECT_SELECTION_STORAGE_KEY)
  } catch {
    // 存储被浏览器禁用时只放弃跨刷新恢复，不影响本次筛选。
  }
  return null
}

export function writeProjectSelection(
  projectId: number | null,
  storage: ProjectSelectionStorage | null = browserStorage(),
): void {
  if (!storage) return

  try {
    if (projectId === null) {
      storage.removeItem(PROJECT_SELECTION_STORAGE_KEY)
      return
    }
    storage.setItem(PROJECT_SELECTION_STORAGE_KEY, String(projectId))
  } catch {
    // 隐私模式或配额异常不能阻断项目切换。
  }
}
