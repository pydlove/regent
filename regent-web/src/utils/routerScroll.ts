export type SavedScrollPosition = {
  left: number
  top: number
}

export type RouterScrollTarget =
  | SavedScrollPosition
  | {
      el: string
      top: number
      behavior: 'smooth'
    }

/**
 * 普通页面跳转从顶部开始；浏览器前进后退仍恢复原位置，带锚点的页面继续定位到锚点。
 */
export function resolveRouterScroll(
  savedPosition: SavedScrollPosition | null,
  hash: string,
): RouterScrollTarget {
  if (savedPosition) return savedPosition
  if (hash) return { el: hash, top: 16, behavior: 'smooth' }
  return { left: 0, top: 0 }
}
