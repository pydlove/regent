export type RequirementImportIdentity = {
  title: string
}

export type ExistingRequirementIdentity = RequirementImportIdentity

function normalize(value: string): string {
  return value.normalize('NFKC').replace(/\s+/g, ' ').trim().toLocaleLowerCase('zh-CN')
}

function identityOf(requirement: RequirementImportIdentity): string | null {
  const title = normalize(requirement.title)
  return title || null
}

export function findDuplicateImportRows(
  rows: RequirementImportIdentity[],
  existing: ExistingRequirementIdentity[],
): (string | null)[] {
  const existingKeys = new Set(
    existing
      .map(identityOf)
      .filter((value): value is string => value !== null),
  )
  const seen = new Map<string, number>()

  return rows.map((row, index) => {
    const key = identityOf(row)
    if (key === null) return null
    if (existingKeys.has(key)) return '需求池已存在同名需求'
    const firstIndex = seen.get(key)
    if (firstIndex !== undefined) return `与文件第 ${firstIndex + 1} 行名称重复`
    seen.set(key, index)
    return null
  })
}
