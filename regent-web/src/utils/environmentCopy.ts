export type EnvironmentCopyKeyInput = {
  scope: string
  projectId: number | null
  key: string
}

export function nextEnvironmentCopyKey(
  source: EnvironmentCopyKeyInput,
  items: EnvironmentCopyKeyInput[],
): string {
  const base = source.key.replace(/_COPY(?:_\d+)?$/i, '')
  const existing = new Set(
    items
      .filter((item) =>
        item.scope === source.scope && item.projectId === source.projectId,
      )
      .map((item) => item.key.toLowerCase()),
  )

  for (let sequence = 1; ; sequence += 1) {
    const suffix = sequence === 1 ? '_COPY' : `_COPY_${sequence}`
    const candidate = `${base.slice(0, 128 - suffix.length)}${suffix}`
    if (!existing.has(candidate.toLowerCase())) return candidate
  }
}
