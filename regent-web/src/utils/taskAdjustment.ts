export type AdjustmentTaskCandidate = {
  id: number
  title: string
}

export function filterAdjustmentSourceTasks<T extends AdjustmentTaskCandidate>(
  tasks: readonly T[],
  targetTaskId: number | null | undefined,
  keyword: string,
): T[] {
  const normalized = keyword.trim().toLocaleLowerCase('zh-CN')
  return tasks.filter((task) => {
    if (task.id === targetTaskId) return false
    if (!normalized) return true
    return (
      String(task.id).includes(normalized) ||
      task.title.toLocaleLowerCase('zh-CN').includes(normalized)
    )
  })
}
