export type PluginFilterStatus = 'all' | 'enabled' | 'disabled'

export type PluginCatalogItem = {
  name: string
  description?: string | null
  tags: string[]
  filename: string
  enabled: boolean
  currentRevision?: number
}

export type PluginCatalogFilter = {
  keyword?: string
  status?: PluginFilterStatus
  tag?: string | null
}

export const MAX_TASK_PLUGINS = 8

function normalized(value: string): string {
  return value.trim().toLocaleLowerCase()
}

export function matchesPlugin(plugin: PluginCatalogItem, keyword: string): boolean {
  const query = normalized(keyword)
  if (!query) return true

  return [
    plugin.name,
    plugin.description ?? '',
    plugin.filename,
    plugin.currentRevision === undefined ? '' : `v${plugin.currentRevision}`,
    ...plugin.tags,
  ].some((value) => normalized(value).includes(query))
}

export function filterPlugins<T extends PluginCatalogItem>(
  plugins: T[],
  filter: PluginCatalogFilter = {},
): T[] {
  const status = filter.status ?? 'all'
  const selectedTag = filter.tag ? normalized(filter.tag) : ''

  return plugins.filter((plugin) => {
    if (status === 'enabled' && !plugin.enabled) return false
    if (status === 'disabled' && plugin.enabled) return false
    if (selectedTag && !plugin.tags.some((tag) => normalized(tag) === selectedTag)) return false
    return matchesPlugin(plugin, filter.keyword ?? '')
  })
}

/** 标签按使用频次排列，只保留最常用的一行，避免标签越多筛选区越难扫。 */
export function pluginTagOptions(
  plugins: PluginCatalogItem[],
  limit = 8,
): { value: string; count: number }[] {
  const counts = new Map<string, { value: string; count: number }>()

  for (const plugin of plugins) {
    for (const rawTag of plugin.tags) {
      const tag = rawTag.trim()
      if (!tag) continue
      const key = normalized(tag)
      const existing = counts.get(key)
      if (existing) existing.count += 1
      else counts.set(key, { value: tag, count: 1 })
    }
  }

  return [...counts.values()]
    .sort((left, right) => right.count - left.count || left.value.localeCompare(right.value, 'zh-CN'))
    .slice(0, limit)
}
