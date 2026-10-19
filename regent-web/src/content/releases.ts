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
export const CURRENT_RELEASE: AppRelease = {
  version: 'feature_V1.0.2_R100B002',
  label: '功能增强',
  releasedAt: '2026-10-19',
  summary:
    '新增历史版本浏览、远程部署与 Codex 执行器自检，并完善项目资料生成与任务入口体验。',
  highlights: [
    {
      title: '历史版本记录',
      description:
        '版本入口可以切换查看当前与历史版本，完整保留每个版本的发布时间、摘要和主要更新项。',
    },
    {
      title: '远程部署',
      description:
        '录入服务器 SSH 信息和部署脚本后可直接发起远程部署，执行状态与日志集中保留，密码不会落库。',
    },
    {
      title: 'Codex 执行器自检',
      description:
        '设置页可检查本机 Codex 的配置命令、实际路径、版本和可用状态，不可用时给出安装与排查提示。',
    },
    {
      title: '项目资料生成',
      description:
        '注册项目后由 Codex 只读分析工作区，生成技术栈、目录、启动验证与风险说明，减少执行前的人工补充。',
    },
  ],
}

const PREVIOUS_RELEASE: AppRelease = {
  version: 'feature_V1.0.2_R100B001',
  label: '功能更新',
  releasedAt: '2026-10-19',
  summary:
    '新增任务草稿自动保存与本机 Whisper 语音输入，完善测试用例与分支执行流程，并优化任务详情在移动端的操作体验。',
  highlights: [
    {
      title: '任务草稿自动保存',
      description:
        '新建任务时边写边保存项目和指令，关闭弹框后下次继续；连续输入也会定期落一次草稿。',
    },
    {
      title: '本机语音输入',
      description:
        '录音每 2.5 秒切段并交给本机 Whisper 转写，文字边录边追加到指令；音频不写数据库。',
    },
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
      title: '轻量分支流程',
      description:
        '任务默认在当前分支执行；只有 AI 主动请求隔离时，平台才在完成后创建 feature 分支。',
    },
    {
      title: '测试用例管理',
      description:
        '支持 Web / 接口测试用例、任务完成自动执行，以及单条或批量手动执行并保留结果。',
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
      title: '移动端任务详情',
      description:
        '长记录独立滚动，批准、驳回、重试等操作固定在面板底部，手机上不用再翻到底。',
    },
  ],
}

/** 最新版本永远排在第一位；历史详情直接从这里读取，避免再维护一份副本。 */
export const RELEASES: readonly AppRelease[] = [CURRENT_RELEASE, PREVIOUS_RELEASE]

export const APP_VERSION = CURRENT_RELEASE.version
