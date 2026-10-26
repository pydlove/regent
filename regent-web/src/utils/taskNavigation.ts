/** 路由里的任务号、项目号都只接受正整数；非法参数退回空值，不把错误带进请求。 */
function parsePositiveIdQuery(value: unknown): number | null {
  const raw = Array.isArray(value) ? value[0] : value
  if (typeof raw !== 'string' && typeof raw !== 'number') return null

  const text = String(raw).trim()
  if (!/^[1-9]\d*$/.test(text)) return null

  const id = Number(text)
  return Number.isSafeInteger(id) ? id : null
}

export const parseTaskIdQuery = parsePositiveIdQuery

export const parseProjectIdQuery = parsePositiveIdQuery
