/** 把服务端返回的 `/api/...` 资源地址转换为 MobileApi 使用的相对路径。 */
export function toMobileApiPath(resourceUrl: string): string {
  try {
    const absolute = new URL(resourceUrl)
    const markerIndex = absolute.pathname.indexOf('/api/')
    if (markerIndex >= 0) {
      return `${absolute.pathname.slice(markerIndex + 4)}${absolute.search}`
    }
  } catch {
    // 相对地址继续走下面的字符串处理。
  }
  if (resourceUrl.startsWith('/api/')) return resourceUrl.slice(4)
  if (resourceUrl.startsWith('api/')) return `/${resourceUrl.slice(4)}`
  return resourceUrl
}
