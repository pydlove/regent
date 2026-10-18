export type ReleaseHighlight = {
  title: string
  description: string
}

export type AppRelease = {
  version: string
  label: string
  releasedAt: string
  summary: string
  highlights: ReleaseHighlight[]
}

/** 版本号是产品对外口径，改动时连同下面的发布说明一起更新。 */
export const APP_VERSION = 'feature_V1.0.1_R100B001'

export const CURRENT_RELEASE: AppRelease = {
  version: APP_VERSION,
  label: '首个版本',
  releasedAt: '2026-10-17',
  summary:
    '首个可用版本，打通「录入任务 → AI 执行 → 分级决策 → 人工批准 → 断点续跑 → 结果留痕」的完整闭环。',
  highlights: [
    {
      title: '任务闭环',
      description:
        '从录入、排队、派发到完成、失败或终止，状态与执行记录集中在同一个列表里。',
    },
    {
      title: '分级决策',
      description:
        '按 L0–L3 仲裁，L0/L1 自主留痕，L2/L3 挂起等待批准；批准后从原会话断点续跑。',
    },
    {
      title: '计划批次',
      description:
        '把多个任务编成计划，按定时或手动触发，执行后保留本轮结果与总结。',
    },
    {
      title: '项目与能力复用',
      description:
        '维护项目工作区、环境信息和固定版本插件，执行时按需注入。',
    },
    {
      title: '缺陷与运行审计',
      description:
        '执行中发现的问题可进入 Bug 单；每次尝试保留 Token、耗时、执行过程与原始事件。',
    },
    {
      title: 'macOS 界面与移动端',
      description:
        '紧凑表格、发丝线、深浅色主题，以及可在手机上完成批准与驳回的窄屏布局。',
    },
  ],
}
