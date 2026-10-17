/** 任务页只接受正整数任务号；非法查询参数退回普通列表，不把错误带进请求。 */
export function parseTaskIdQuery(value: unknown): number | null {
  const raw = Array.isArray(value) ? value[0] : value
  if (typeof raw !== 'string' && typeof raw !== 'number') return null

  const text = String(raw).trim()
  if (!/^[1-9]\d*$/.test(text)) return null

  const taskId = Number(text)
  return Number.isSafeInteger(taskId) ? taskId : null
}
