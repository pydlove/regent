export type TableColumn = {
  key: string
  label: string
  align?: 'left' | 'right'
  width?: string
  /** 数字列：强制等宽数字。macOS 的数据表格默认如此，否则同一列各位数宽度不等，扫读时左边缘会跳 */
  numeric?: boolean
}
