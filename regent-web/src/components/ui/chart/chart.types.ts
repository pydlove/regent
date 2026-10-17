/** 环形图的一段。{@code toneClass} 是描边用的工具类（如 `text-success`），不是色值。 */
export type DonutSegment = {
  key: string
  label: string
  value: number
  toneClass: string
}

/** 柱状图的一根。{@code highlight} 用于标出「今天」那一根。 */
export type TrendBar = {
  key: string
  label: string
  done: number
  failed: number
  highlight?: boolean
}

/** 趋势折线图的一天。与柱状图共用字段，避免工作台为两种图准备两份数据。 */
export type TrendDatum = TrendBar
