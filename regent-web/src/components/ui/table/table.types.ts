export type TableColumn = {
  key: string
  label: string
  align?: 'left' | 'right'
  width?: string
  /** 数字列：强制等宽数字。macOS 的数据表格默认如此，否则同一列各位数宽度不等，扫读时左边缘会跳 */
  numeric?: boolean
  /**
   * 窄屏收起断点：视口小于该断点时这一列隐藏。
   * 手机装不下整张表，次要列先收起来 —— 完整信息在详情面板与弹框里，列表只保留能定位到那一行的列。
   */
  hideBelow?: 'sm' | 'md' | 'lg'
}
