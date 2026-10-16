import type { DecisionLevel, RunStatus, TaskSource, TaskStatus } from '@/types/task'

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
  branchName: string
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

/**
 * 任务的一张图片附件。
 *
 * 只带 `contentUrl` 不带磁盘路径：图片放在平台侧哪个目录是服务端实现细节，
 * 界面要看图就访问这个地址（见 AttachmentController）。
 */
export type TaskAttachment = {
  id: number
  taskId: number
  /** 用户的原始文件名，只用于展示与「另存为」 */
  filename: string
  contentType: string
  sizeBytes: number
  /** 形如 `/api/attachments/{id}/content`，可以直接塞进 `<img src>` */
  contentUrl: string
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
  recommendation: string | null
  reversible: boolean
  blastRadius: string | null
  resolvedBy: string | null
  resolvedChoice: string | null
  /** 决议时留下的说明。驳回时必填 —— 「为什么否掉」是复盘时唯一能读到的信息 */
  resolutionNote: string | null
  raisedAt: string
  resolvedAt: string | null
}
