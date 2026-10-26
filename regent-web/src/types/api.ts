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
  mustChangePassword: boolean
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

export type TokenUsageRange =
  | 'TODAY'
  | 'LAST_24_HOURS'
  | 'LAST_7_DAYS'
  | 'LAST_14_DAYS'
  | 'LAST_30_DAYS'
  | 'ALL'

/** 某自然日的已结算 Token；后端已按当前用户时区切天。 */
export type DailyTokenUsagePoint = {
  date: string
  input: number
  cachedInput: number
  output: number
  total: number
  runCount: number
}

/** 当天或近 24 小时的整点 Token 趋势。 */
export type HourlyTokenUsagePoint = {
  bucketStart: string
  input: number
  cachedInput: number
  output: number
  total: number
  runCount: number
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
  requirementId: number | null
  source: TaskSource
  title: string
  prompt: string
  status: TaskStatus
  priority: number
  threadId: string | null
  /** 基于哪条任务继续创建；非空表示复用该任务的 Codex 会话 */
  continuedFromTaskId: number | null
  branchName: string | null
  decisionId: number | null
  attemptCount: number
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
  updatedAt: string
}

/** 执行期间新增的一条调整方向。 */
export type TaskAdjustment = {
  id: number
  taskId: number
  source: 'TASK' | 'TEMPORARY'
  sourceTaskId: number | null
  sourceTitle: string | null
  content: string
  status: 'PENDING' | 'APPLIED'
  appliedRunId: number | null
  appliedAt: string | null
  createdAt: string
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
  /** 设备代理的稳定 UUID；通用外部协议未上报设备号时为 null */
  deviceId: string | null
  /** 外部执行者的租约到期时间；没有外部执行者持有（或已收场）时为 null */
  leaseExpiresAt: string | null
  /** 外部执行者最后一次续租时间 */
  lastHeartbeatAt: string | null
  /** 这次执行最后一条事件时间 */
  lastEventAt: string | null
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
  diagnostic: ExecutionDiagnostic
  /** 末条消息原文（codex 的 -o 文件内容），复盘用 */
  rawResult: string | null
  events: RunEvent[]
}

export type ReviewSnapshotStatus =
  | 'WAITING_REVIEW'
  | 'IN_REVIEW'
  | 'CHANGES_REQUESTED'
  | 'APPROVED'
  | 'COMMITTED'
  | 'REJECTED'
  | 'EXPIRED'

export type ReviewRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'
export type ReviewFindingSeverity = 'BLOCKER' | 'CRITICAL' | 'MAJOR' | 'MINOR' | 'INFO'
export type ReviewFindingStatus = 'PENDING' | 'CONFIRMED' | 'FALSE_POSITIVE' | 'FIXED'
export type ReviewEvidenceStatus = 'PASSED' | 'FAILED' | 'NOT_EXECUTED' | 'UNAVAILABLE'
export type ReviewDecisionType = 'APPROVED' | 'CHANGES_REQUESTED' | 'REJECTED'
export type ReviewUnitDecisionStatus = 'PENDING' | 'APPROVED' | 'CHANGES_REQUESTED' | 'REJECTED'

export type ReviewHunk = {
  filePath: string
  oldStart: number
  oldLines: number
  newStart: number
  newLines: number
  content: string
}

export type ReviewChangeUnit = {
  id: number
  sequenceNo: number
  kind: 'BUSINESS' | 'GENERATED' | 'LOCKFILE' | 'FORMATTING' | 'BINARY' | 'UNKNOWN'
  riskLevel: ReviewRiskLevel
  title: string
  reason: string
  decisionQuestion: string
  moduleName: string | null
  filePaths: string[]
  keyHunks: ReviewHunk[]
  requiresView: boolean
  viewedAt: string | null
  decisionStatus: ReviewUnitDecisionStatus
  decisionComment: string | null
  decidedBy: number
  decidedAt: string | null
}

export type ReviewFinding = {
  id: number
  changeUnitId: number | null
  severity: ReviewFindingSeverity
  status: ReviewFindingStatus
  title: string
  description: string
  impact: string | null
  filePath: string | null
  lineStart: number | null
  lineEnd: number | null
  suggestedAction: string | null
  createdAt: string
  updatedAt: string
}

export type ReviewEvidence = {
  id: number
  changeUnitId: number | null
  type: 'TEST' | 'BUILD' | 'LOG' | 'SCREENSHOT' | 'API' | 'DATABASE_MIGRATION' | 'DEPLOYMENT_PRECHECK'
  status: ReviewEvidenceStatus
  title: string
  command: string | null
  summary: string | null
  sourceType: string
  sourceId: number | null
  occurredAt: string
}

export type ReviewSnapshot = {
  id: number
  taskId: number
  taskRunId: number
  projectId: number
  baseRef: string | null
  headRef: string | null
  gitRepository: boolean
  diffHash: string
  fileCount: number
  additions: number
  deletions: number
  status: ReviewSnapshotStatus
  analysisStatus: 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED'
  analysisError: string | null
  diffTruncated: boolean
  requiredUnitCount: number
  viewedRequiredUnitCount: number
  approvedUnitCount: number
  changeRequestedUnitCount: number
  rejectedUnitCount: number
  unresolvedBlockerCount: number
  canApprove: boolean
  deployable: boolean
  commitSha: string | null
  commitMessage: string | null
  committedBy: number
  committedAt: string | null
  reviewedAt: string | null
  createdAt: string
  updatedAt: string
  changeUnits: ReviewChangeUnit[]
  findings: ReviewFinding[]
  evidence: ReviewEvidence[]
}

export type ReviewDecision = {
  id: number
  snapshotId: number
  decision: ReviewDecisionType
  operatorId: number
  comment: string | null
  createdAt: string
}

export type TaskReview = {
  snapshot: ReviewSnapshot | null
  decisions: ReviewDecision[]
  progress: ReviewGenerationProgress
}

export type ReviewGenerationProgress = {
  status: 'IDLE' | 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  stage: 'idle' | 'queued' | 'diff' | 'analysis' | 'fallback' | 'saving' | 'completed' | 'failed' | 'timeout'
  message: string
  updatedAt: string | null
  error: string | null
}

export type ReviewDiff = {
  content: string
  truncated: boolean
}

export type ReviewDecisionAction = {
  snapshot: ReviewSnapshot
  decision: ReviewDecision
  continuationTaskId: number | null
}

export type ReviewGitCommit = {
  enabled: boolean
  canCommit: boolean
  reason: string
  projectName: string
  branchName: string
  headCommit: string
  ticket: string
  fileCount: number
  filePaths: string[]
  suggestedMessage: string
  validationPattern: string
  validationHint: string
  commitSha: string
  committedAt: string | null
}

export type ExecutionDiagnostic = {
  healthStatus: ExecutionControlTowerStatus | 'FAILED' | 'UNKNOWN'
  failureCategory: string | null
  lastSuccessStage: string
  failureStage: string | null
  retryable: boolean
  requiresDeviceChange: boolean
  requiresDecision: boolean
  summary: string | null
  lastActivityAt: string | null
}

export type ExecutionControlTowerStatus =
  | 'HEALTHY'
  | 'SLOW'
  | 'STALLED'
  | 'DEVICE_LOST'
  | 'FAILED'
  | 'UNKNOWN'

export type ExecutionControlTowerEntry = {
  executionType: 'TASK' | 'DEPLOYMENT'
  businessId: number
  runId: number | null
  attemptNo: number | null
  projectId: number
  projectName: string
  name: string
  businessStatus: string
  healthStatus: ExecutionControlTowerStatus
  stage: string
  statusDetail: string
  deviceName: string
  executor: string | null
  failureCategory: string | null
  startedAt: string | null
  lastActivityAt: string | null
  finishedAt: string | null
  retryable: boolean
  stoppable: boolean
  reviewable: boolean
}

export type ExecutionControlTowerSnapshot = {
  activeCount: number
  queuedCount: number
  blockedCount: number
  abnormalCount: number
  totalCount: number
  entries: ExecutionControlTowerEntry[]
}

export type RequirementStatus =
  | 'DRAFT'
  | 'CLARIFYING'
  | 'READY'
  | 'PLANNED'
  | 'RUNNING'
  | 'PARTIAL'
  | 'DONE'
  | 'CANCELLED'

export type RequirementPoolStatus = 'POOL' | 'IN_REVIEW' | 'FORMAL' | 'REJECTED'

export type RequirementSource = 'MANUAL' | 'IMPORT' | 'AI'

export type RequirementTaskDraft = {
  key: string
  title: string
  objective: string
  scope: string[]
  outOfScope: string[]
  acceptanceCriteria: string[]
  dependencies: string[]
  priority: number
  risk: string
  suggestedOrder: number
}

export type RequirementTask = {
  id: number
  title: string
  status: TaskStatus
  priority: number
  dependencies: number[]
}

export type Requirement = {
  id: number
  projectId: number
  initiativeId: number | null
  versionId: number | null
  title: string
  rawText: string
  acceptanceText: string | null
  clarificationText: string | null
  status: RequirementStatus
  poolStatus: RequirementPoolStatus
  source: RequirementSource
  reviewComment: string | null
  reviewedBy: number | null
  reviewedAt: string | null
  clarificationQuestions: string[]
  assumptions: string[]
  taskDrafts: RequirementTaskDraft[]
  tasks: RequirementTask[]
  createdAt: string
  updatedAt: string
  plannedAt: string | null
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

/** 任务绑定的文档。内容走 contentUrl 下载，不在列表里传字节。 */
export type TaskDocument = {
  id: number
  taskId: number
  libraryDocumentId: number | null
  filename: string
  contentType: string
  sizeBytes: number
  contentUrl: string
  savedToLibrary: boolean
}

/** 项目文件库中的可复用文档。 */
export type LibraryDocument = {
  id: number
  projectId: number
  libraryType: LibraryType
  sourceTaskId: number | null
  filename: string
  contentType: string
  sizeBytes: number
  contentUrl: string
  createdAt: string
}

export type LibraryType =
  | 'GENERAL'
  | 'DEVELOPMENT_RULE'
  | 'REQUIREMENT'
  | 'DESIGN'
  | 'TESTING'

export type CodeStandardScope =
  | 'GENERAL'
  | 'FRONTEND'
  | 'BACKEND'
  | 'TESTING'
  | 'SECURITY'
  | 'DATABASE'
  | 'DEPLOYMENT'

export type CodeStandard = {
  id: number
  projectId: number
  name: string
  description: string | null
  scope: CodeStandardScope
  enabled: boolean
  defaultApply: boolean
  sortOrder: number
  archived: boolean
  sourceProjectId: number | null
  sourceStandardId: number | null
  sourceVersionNo: number | null
  currentVersion: number
  filename: string | null
  sizeBytes: number
  contentHash: string | null
  contentUrl: string | null
  createdAt: string
  updatedAt: string
}

export type CodeStandardVersion = {
  id: number
  standardId: number
  versionNo: number
  filename: string
  contentType: string
  sizeBytes: number
  contentHash: string
  contentUrl: string
  createdAt: string
}

export type TaskCodeStandard = {
  id: number
  taskId: number
  standardId: number
  standardVersionId: number
  name: string
  versionNo: number
  filename: string
  contentType: string
  sizeBytes: number
  contentHash: string
  contentUrl: string
  sourceProjectId: number | null
  createdAt: string
}

export type CodeStandardCopyResult = {
  copied: string[]
  skipped: Array<{ name: string; reason: string }>
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
  organizationId: number | null
  code: string
  name: string
  /** 本机代码目录；研发项目未绑定时为空 */
  path: string | null
  /** 项目级自动决策阈值，高于它的决策一律升级人工 */
  defaultDecisionLevel: DecisionLevel
  /** Git Tag 自动解析，或使用项目设置的固定版本 */
  versionSource: 'GIT_TAG' | 'MANUAL'
  /** Git Tag 不可用时使用；手动模式必填 */
  fallbackVersion: string | null
  /** 暂停开关；关掉之后不再派发新任务，但录入不受影响 */
  enabled: boolean
  /** Codex 生成的项目摘要状态 */
  summaryStatus: 'NOT_GENERATED' | 'GENERATING' | 'READY' | 'FAILED'
  /** Codex 只读分析项目后生成的 Markdown 资料 */
  summary: string | null
  /** 最近一次生成失败原因；成功或未生成时为 null */
  summaryError: string | null
  /** 最近一次成功生成时间 */
  summaryGeneratedAt: string | null
}

/** 项目当前提交可解析出的发布版本。 */
export type ProjectVersion = {
  version: string | null
  source: 'GIT_TAG' | 'MANUAL' | 'FALLBACK' | 'NONE'
  available: boolean
  message: string
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
  /** scope=GLOBAL 时为 null；scope=PROJECT 时为第一个归属项目。 */
  projectId: number | null
  projectIds: number[]
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
export type DeploymentExecutionMode = 'REMOTE_SCRIPT' | 'PROJECT_SCRIPT'

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
  /** 多个归属项目中的主项目，用于兼容旧执行记录。 */
  projectId: number
  projectName: string
  projectIds: number[]
  projectNames: string[]
  serverId: number
  serverName: string
  name: string
  host: string
  port: number
  username: string
  authType: DeploymentAuthType
  remoteDirectory: string
  scriptFilename: string
  scriptHash: string
  scriptSize: number
  executionMode: DeploymentExecutionMode
  projectScriptPath: string | null
  latestRunStatus: DeploymentRunStatus | null
  latestRunAt: string | null
  createdAt: string
  updatedAt: string
}

export type DeploymentDetail = DeploymentSummary & {
  scriptContent: string
  credentialConfigured: boolean
}

/** 可复用的部署服务器；密码永远不在响应里。 */
export type DeploymentServer = {
  id: number
  /** 可在新建部署时选择这台服务器的项目。 */
  projectIds: number[]
  name: string
  host: string
  port: number
  username: string
  authType: DeploymentAuthType
  /** 新建部署选择这台服务器时预填的远程工作目录。 */
  defaultRemoteDirectory: string | null
  credentialConfigured: boolean
  deploymentCount: number
  createdAt: string
  updatedAt: string
}

/** 一处部署脚本规范问题，line 从 1 开始。 */
export type DeploymentScriptIssue = {
  line: number
  code: string
  message: string
}

export type DeploymentScriptValidation = {
  valid: boolean
  issues: DeploymentScriptIssue[]
}

/** Codex 生成的部署脚本，可直接填入新建部署表单。 */
export type DeploymentScriptGeneration = {
  scriptFilename: string
  scriptContent: string
}

export type DeploymentRunSummary = {
  id: number
  deploymentId: number
  projectId: number
  taskId: number | null
  reviewSnapshotId: number | null
  reviewDecisionId: number | null
  projectVersion: string | null
  versionSource: 'GIT_TAG' | 'MANUAL' | 'FALLBACK' | 'NONE' | null
  deploymentName: string
  host: string
  port: number
  username: string
  authType: DeploymentAuthType
  executionMode: DeploymentExecutionMode
  projectScriptPath: string | null
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

/** 企业 Git 提交信息模板与最终文本校验规范。 */
export type GitCommitSettings = {
  enabled: boolean
  defaultType: string
  defaultScope: string
  subjectTemplate: string
  bodyTemplate: string
  validationPattern: string
  validationHint: string
}

export type GitCommitStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED'

/** 平台发起的一次 Git 提交尝试。 */
export type GitCommitSummary = {
  id: number
  projectId: number
  projectName: string
  taskId: number
  taskTitle: string
  ticket: string | null
  message: string
  branchName: string
  commitSha: string | null
  status: GitCommitStatus
  failureReason: string | null
  operatorName: string
  startedAt: string
  finishedAt: string | null
  reviewSnapshotId: number
}

export type GitCommitDetail = {
  summary: GitCommitSummary
  reviewDecisionId: number
  repositoryPath: string
  baseRef: string | null
  filePaths: string[]
  failureReason: string | null
  outputSummary: string | null
}

export type GitCommitPage = {
  items: GitCommitSummary[]
  total: number
  page: number
  size: number
}

export type CodeEditor = {
  id: 'VS_CODE' | 'CURSOR' | 'INTELLIJ_IDEA'
  name: string
  supportsFileNavigation: boolean
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
