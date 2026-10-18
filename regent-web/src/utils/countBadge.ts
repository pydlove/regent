/**
 * 侧边栏等固定宽度位置的角标文本。
 *
 * 精确值最多保留到上限；再大就显示「上限+」。完整数量由调用方放进 title / aria-label，
 * 这样角标不会随着数据增长无限撑宽，也不会把菜单文字挤走。
 */
export function formatCountBadge(value: number, limit = 999): string {
  if (!Number.isFinite(value) || value <= 0) return ''
  const normalized = Math.floor(value)
  return normalized > limit ? `${limit}+` : String(normalized)
}
