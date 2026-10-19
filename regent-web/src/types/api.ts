import type { DecisionLevel, RunStatus, TaskSource, TaskStatus } from '@/types/task'
import type {
  PlanRunStatus,
  PlanStatus,
  PlanTaskStatus,
  PlanTrigger,
} from '@/types/plan'

/*
 * 后端契约的镜像。字段名与 Java 侧的 record 一一对应，不做改名 ——
 * 一旦这里开始「美化」字段名，接口一改就要在两个名字之间做映射，
 * 而映射漏掉的那一个字段会表现为界面上某个位置莫名其妙地空着。
 *
 * 命名对齐的是：
 *   PageResponse            → com.pydlove.regent.interfaces.dto.PageResponse
 *   TaskSummary / TaskDetail → ...execution.interfaces.dto.TaskSummaryResponse / TaskDetailResponse
 *   DecisionSummary          → ...governance.interfaces.dto.DecisionSummaryResponse
 *   Project                  → ...project.interfaces.dto.ProjectResponse
 */

export type PageResponse<T> = {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export type ApiErrorBody = {
  code: string
  message: string
  timestamp: string
}

/** 当前登录用户的公开信息。后端不会返回密码或会话令牌。 */
export type AuthUser = {
  id: number
  username: string
  displayName: string
  email: string | null
  avatarUrl: string | null
}

/** 批量删除中的一个失败项。 */
export type TaskBatchDeleteFailure = {
  taskId: number
  message: string
}

/** 批量删除结果。成功删除与未能删除的任务分开返回。 */
export type TaskBatchDeleteResult = {
  deletedTaskIds: number[]
  failures: TaskBatchDeleteFailure[]
}

/** 批量派发中的一个失败项。 */
export type TaskBatchDispatchFailure = {
  taskId: number
  message: string
}

/** 批量派发结果。成功包括立即起跑与进入排队，失败项留下供用户重试。 */
export type TaskBatchDispatchResult = {
  dispatchedTaskIds: number[]
  failures: TaskBatchDispatchFailure[]
}

/** 工作台趋势柱状图的一天；后端已按天补齐，不存在空档。 */
export type TaskTrendPoint = {
  /** yyyy-MM-dd，按本机时区切天 */
  date: string
  done: number
  failed: number
}

/** 全平台已结算 token；总量与任务列表使用同一口径。 */
export type TaskTokenUsage = {
  input: number
  cachedInput: number
  output: number
  total: number
}

/** 后端的时间一律是 ISO-8601 的 UTC 串（如 2026-10-16T04:41:10.573Z），可空字段用 null。 */
export type TaskSummary = {
  id: number
  projectId: number
  projectName: string
  source: TaskSource
  title: string
  status: TaskStatus
  priority: number
  /** 该任务最新决策的仲裁等级；没有决策过的是 L0 */
  level: DecisionLevel
  branchName: string | null
  /** 正在阻塞它的决策单号；非 null 说明任务卡在等人拍板 */
  decisionId: number | null
  attemptCount: number
  tokens: number
  /** 所有执行记录的运行时长之和；阻塞等人或排队等待的时间不计入 */
  executionMillis: number
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
  updatedAt: string
}

export type TaskDetail = {
  id: number
  projectId: number
  source: TaskSource
  title: string
  prompt: string
  status: TaskStatus
  priority: number
  threadId: string | null
  branchName: string | null
  decisionId: number | null
  attemptCount: number
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
  updatedAt: string
}

/** 当前登录用户尚未提交的任务录入草稿。 */
export type TaskDraft = {
  id: number
  projectId: number | null
  prompt: string
  priority: number
  pluginIds: number[]
  createdAt: string
  updatedAt: string
}

/** 当前草稿查询结果；draft 为 null 表示还没有草稿。 */
export type CurrentTaskDraft = {
  draft: TaskDraft | null
}

/** 一次尝试的列表行。token 三个分量分开给，界面上要回答「这次为什么这么贵」。 */
export type TaskRunSummary = {
  id: number
  taskId: number
  attemptNo: number
  status: RunStatus
  exitCode: number | null
  tokenInput: number
  tokenCachedInput: number
  tokenOutput: number
  tokens: number
  branchName: string | null
  /** 谁在跑这次尝试：`codex-cli` = 平台自己拉起的本机 codex；`agent` = 外部执行者 */
  executor: string
  /** 外部执行者的自称；平台自己拉起的执行器为 null */
  agentName: string | null
  /** 外部执行者的租约到期时间；没有外部执行者持有（或已收场）时为 null */
  leaseExpiresAt: string | null
  summary: string | null
  /** 出参是否符合约定。false 说明这次执行的结论读不懂，界面要标出来 */
  schemaValid: boolean
  changedFiles: string[]
  startedAt: string
  finishedAt: string | null
}

/**
 * 事件流里的一行。
 *
 * payload 是**原文 JSON 文本**而不是解析后的对象：日志面板的用途是「原样看到执行器说了什么」，
 * 做过加工的日志在排错时会骗人，所以这一层不解析、只传。
 */
export type RunEvent = {
  seq: number
  type: string
  payload: string
  occurredAt: string
}

export type TaskRunDetail = {
  run: TaskRunSummary
  /** 末条消息原文（codex 的 -o 文件内容），复盘用 */
  rawResult: string | null
  events: RunEvent[]
}

export type PlanRunSummary = {
  id: number
  planId: number
  planName: string
  trigger: PlanTrigger
  status: PlanRunStatus
  taskCount: number
  progressCount: number
  succeededCount: number
  failedCount: number
  cancelledCount: number
  skippedCount: number
  runningCount: number
  blockedCount: number
  summary: string | null
  startedAt: string
  finishedAt: string | null
}

export type PlanSummary = {
  id: number
  name: string
  status: PlanStatus
  taskCount: number
  scheduledAt: string
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
  latestRun: PlanRunSummary | null
}

export type PlanTask = {
  id: number
  title: string
  taskStatus: TaskStatus | null
  planTaskStatus: PlanTaskStatus | null
}

export type PlanDetail = {
  plan: PlanSummary
  tasks: PlanTask[]
  runs: PlanRunSummary[]
}

export type PlanRunItem = {
  taskId: number
  title: string
  status: PlanTaskStatus
  detail: string | null
  updatedAt: string
}

export type PlanRunDetail = {
  run: PlanRunSummary
  items: PlanRunItem[]
}

/** 图片附件的共同展示字段，任务与 Bug 的缩略图组件只依赖这一层。 */
export type ImageAttachment = {
  id: number
  /** 用户的原始文件名，只用于展示与「另存为」 */
  filename: string
  contentType: string
  sizeBytes: number
  /** 可以直接塞进 `<img src>` */
  contentUrl: string
}

/** 任务的一张图片附件。 */
export type TaskAttachment = ImageAttachment & {
  taskId: number
}

/** Bug 单的一张图片附件。 */
export type BugAttachment = ImageAttachment & {
  bugId: number
}

/**
 * 项目画像。任务必须挂在项目下，所以「新建任务」的弹框要先有这份数据才能提交。
 *
 * 不含技术栈与验证方式：后端的画像行里还没有这两列（见 ProjectProfileRow），
 * 等真有展示或编辑入口时，后端请求/画像/响应与这里的类型一起加。
 */
export type Project = {
  id: number
  code: string
  name: string
  path: string
  /** 项目级自动决策阈值，高于它的决策一律升级人工 */
  defaultDecisionLevel: DecisionLevel
  /** 暂停开关；关掉之后不再派发新任务，但录入不受影响 */
  enabled: boolean
  /** Codex 生成的项目资料状态 */
  summaryStatus: 'NOT_GENERATED' | 'GENERATING' | 'READY' | 'FAILED'
  /** Codex 只读分析项目后生成的 Markdown 资料 */
  summary: string | null
  /** 最近一次生成失败原因；成功或未生成时为 null */
  summaryError: string | null
  /** 最近一次成功生成时间 */
  summaryGeneratedAt: string | null
}

/** 项目工作区的当前 Git 分支；没有 Git 管理或处于游离 HEAD 时为 null。 */
export type ProjectGitBranch = {
  branchName: string | null
}

export type EnvironmentScope = 'GLOBAL' | 'PROJECT'

/** 一条任务执行时可用的环境信息。 */
export type EnvironmentEntry = {
  id: number
  scope: EnvironmentScope
  /** scope=GLOBAL 时为 null；scope=PROJECT 时必填 */
  projectId: number | null
  key: string
  value: string
  tags: string[]
  description: string | null
  /** 密码值：列表与详情默认脱敏 */
  secret: boolean
  createdAt: string
  updatedAt: string
}

/** 可复用插件列表项；列表不返回大段正文。 */
export type PluginSummary = {
  id: number
  name: string
  description: string | null
  tags: string[]
  /** 随发行包同步，正文只读；仍可启停。 */
  builtin: boolean
  enabled: boolean
  currentRevision: number
  filename: string
  contentHash: string
  createdAt: string
  updatedAt: string
}

/** 插件详情与编辑表单使用的完整版本。 */
export type PluginDetail = PluginSummary & {
  content: string
}

/** 任务绑定的插件固定版本。 */
export type TaskPluginBinding = {
  pluginId: number
  name: string
  revision: number
  filename: string
  contentHash: string
  enabled: boolean
  boundAt: string
}

export type TestCaseType = 'WEB' | 'API'

export type TestCaseRunStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'TIMEOUT'
  | 'INTERRUPTED'
  | 'SKIPPED'

/** 测试用例列表项。 */
export type TestCaseSummary = {
  id: number
  projectId: number
  projectName: string
  name: string
  type: TestCaseType
  description: string | null
  command: string | null
  scriptFilename: string | null
  scriptHash: string | null
  scriptSize: number
  enabled: boolean
  latestRunStatus: TestCaseRunStatus | null
  latestRunAt: string | null
  createdAt: string
  updatedAt: string
}

/** 测试用例详情，额外返回可编辑的脚本正文。 */
export type TestCaseDetail = TestCaseSummary & {
  scriptContent: string | null
}

/** 任务绑定的测试用例。 */
export type TaskTestCaseBinding = {
  testCaseId: number
  name: string
  type: TestCaseType
  command: string | null
  scriptFilename: string | null
  enabled: boolean
  boundAt: string
}

export type TestCaseRunSummary = {
  id: number
  projectId: number
  taskId: number | null
  trigger: 'TASK_COMPLETION' | 'MANUAL'
  testCaseId: number
  testCaseName: string
  type: TestCaseType
  status: TestCaseRunStatus
  exitCode: number | null
  summary: string | null
  queuedAt: string
  startedAt: string | null
  finishedAt: string | null
}

export type TestCaseRunDetail = TestCaseRunSummary & {
  command: string | null
  scriptFilename: string | null
  scriptHash: string | null
  output: string | null
}

export type TestCaseBatchRunResult = {
  runs: TestCaseRunSummary[]
  failures: { testCaseId: number; message: string }[]
}

export type DeploymentAuthType = 'KEY' | 'PASSWORD'

export type DeploymentRunStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'TIMEOUT'
  | 'INTERRUPTED'

/** 部署配置列表项；脚本正文只在详情接口返回。 */
export type DeploymentSummary = {
  id: number
  projectId: number
  projectName: string
  name: string
  host: string
  port: number
  username: string
  authType: DeploymentAuthType
  remoteDirectory: string
  scriptFilename: string
  scriptHash: string
  scriptSize: number
  latestRunStatus: DeploymentRunStatus | null
  latestRunAt: string | null
  createdAt: string
  updatedAt: string
}

export type DeploymentDetail = DeploymentSummary & {
  scriptContent: string
  credentialConfigured: boolean
}

export type DeploymentRunSummary = {
  id: number
  deploymentId: number
  projectId: number
  deploymentName: string
  host: string
  port: number
  username: string
  authType: DeploymentAuthType
  status: DeploymentRunStatus
  exitCode: number | null
  summary: string | null
  queuedAt: string
  startedAt: string | null
  finishedAt: string | null
}

export type DeploymentRunDetail = DeploymentRunSummary & {
  remoteDirectory: string
  scriptFilename: string
  scriptHash: string
  log: string
}

/** 平台级执行设置；修改后立即影响新任务的领取，不终止已经运行的任务。 */
export type ExecutionSettings = {
  maxConcurrentTasks: number
  runningTaskCount: number
}

/** 设置页的 Codex 可执行文件检测结果；可通过不等于已登录。 */
export type CodexRuntime = {
  available: boolean
  configuredCommand: string
  executablePath: string | null
  version: string | null
  message: string
  checkedAt: string
}

export type CodexInstallMethod = 'npm' | 'brew'

export type CodexInstallResult = {
  success: boolean
  method: string
  message: string
  output: string
  runtime: CodexRuntime
}

export type RelayState = 'UNCONFIGURED' | 'DISABLED' | 'ONLINE' | 'DEGRADED' | 'OFFLINE'

/** 中继服务器与本地连接器的实时状态。 */
export type RelayStatus = {
  state: RelayState
  connectorOnline: boolean | null
  queueDepth: number | null
  latencyMillis: number | null
  checkedAt: string | null
  message: string
}

/** 中继连接、安装与状态监听设置；SSH 密码和共享密钥都不经过读取接口。 */
export type RelaySettings = {
  serverUrl: string
  monitoringEnabled: boolean
  serverIp: string
  sshUsername: string
  installDirectory: string
  status: RelayStatus
}

export type RelayInstallResult = {
  success: boolean
  installDirectory: string
  message: string
}

/**
 * 目录浏览结果。字段与后端 DirectoryListingResponse 逐字对齐。
 *
 * 「注册项目」的路径字段用它做选择器：后端与 codex 跑在同一台机器上，
 * 所以这里列出来的目录树就是执行时真正的工作目录。
 */
export type DirectoryListing = {
  path: string
  /** 根目录上是 null，界面据此禁用「上一级」 */
  parent: string | null
  entries: DirectoryEntry[]
  /** 子目录比返回的多（只给了前 500 个），界面要如实说一声 */
  truncated: boolean
}

export type DirectoryEntry = {
  name: string
  path: string
}

export type DecisionSummary = {
  id: number
  taskId: number
  taskTitle: string
  projectId: number
  projectName: string
  aiLevel: DecisionLevel
  effectiveLevel: DecisionLevel
  /** AI 自评与仲裁结果不一致 = 规则把等级抬上去了，这是最该被看见的信息 */
  levelEscalated: boolean
  matchedRuleName: string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'AUTO_RESOLVED'
  question: string
  options: string[]
  recommendation: string | null
  reversible: boolean
  blastRadius: string | null
  resolvedBy: string | null
  resolvedChoice: string | null
  /** 决议时留下的可选说明。 */
  resolutionNote: string | null
  raisedAt: string
  resolvedAt: string | null
}
