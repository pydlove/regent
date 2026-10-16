/*
 * 展示格式化。后端一律给 UTC 的 ISO 串，转成本地时间是**展示层**的事 ——
 * 让后端按浏览器时区返回时间看起来更省事，但那会让同一份数据在不同客户端上
 * 得到不同结果，缓存与日志也就没法对齐了。
 */

/** 耗时：从开始到现在（未结束）或到结束时刻。没有开始时间就显示占位符。 */
export function formatDuration(start: string | null, end: string | null): string {
  if (!start) return '—'
  const from = new Date(start).getTime()
  const to = end ? new Date(end).getTime() : Date.now()
  const seconds = Math.max(0, Math.round((to - from) / 1000))
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  if (minutes < 60) return `${minutes}m${String(rest).padStart(2, '0')}s`
  const hours = Math.floor(minutes / 60)
  return `${hours}h${String(minutes % 60).padStart(2, '0')}m`
}

/** 相对时间：列表里的时间几乎都在最近几小时内，说「3 分钟前」比说「05:47:10」更快读懂。 */
export function formatRelative(iso: string | null): string {
  if (!iso) return '—'
  const seconds = Math.round((Date.now() - new Date(iso).getTime()) / 1000)
  if (seconds < 60) return '刚刚'
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分钟前`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} 小时前`
  return `${Math.floor(seconds / 86400)} 天前`
}

/** Token 数上万之后逐位写出来没人读得下去，缩成 4.3k 这种形态。 */
export function formatTokens(value: number): string {
  // 0 表示「还没有结算过的尝试」（比如正在执行的任务），写 0 会让人以为真的没耗 token
  if (value <= 0) return '—'
  if (value < 1000) return String(value)
  if (value < 1_000_000) return `${(value / 1000).toFixed(1)}k`
  return `${(value / 1_000_000).toFixed(1)}M`
}

/** 图片大小。截图大多落在几百 KB 到几 MB，进一位小数就够读了。 */
export function formatBytes(bytes: number): string {
  if (bytes <= 0) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
