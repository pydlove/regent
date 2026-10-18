# Regent AI 任务执行平台

> 多项目需求自动执行平台，带分级决策升级。
>
> 名字直说它是干什么的：需求录进来，自动派给 Codex 执行；遇到要拍板的地方按 L0–L3 分级——
> 阈值内自主决断并留痕，超阈值挂起等人批准，批完断点续跑。
> 仓库名与代码名沿用 `Regent`（包名 `com.pydlove.regent`、产物 `regent-boot.jar`）。

## 它解决什么问题

手上十多个项目，需求只能一点点交代，交代完还得一直盯着——每次卡在某个决策上都在等你回话。

Regent 换个做法：需求一次性录进平台，平台逐个调起 Codex 执行。过程中遇到需要拍板的地方
按等级分流——**L0/L1 由 AI 自主决断并留痕，L2/L3 挂起并推送到手机等你批**。
你只需要在收到决策消息时介入，其余时间它自己跑。跑完标状态，过程中冒出来的问题进 Bug 单等修复。

目标是：**从"一直盯着"变成"手机上有没有收到决策消息"**。

## 核心机制

| 机制 | 说明 |
|---|---|
| 需求录入 | 按项目录入需求，自动拆解为可执行任务 |
| 分级决策 | L0–L3 四级；AI 自评等级与规则命中等级取高者（就高不就低） |
| 决策留痕 | 每条决策记录「谁做的决定」与依据规则，可事后追溯 |
| 断点续跑 | 依赖 `codex exec` 的 `thread_id`，人工批准后从断点接着执行，上下文不丢 |
| 重启恢复 | 平台重启后把遗留的内部执行标记为「已中断」，任务自动重新排队；有 `thread_id` 时继续原会话 |
| 缺陷闭环 | 执行中发现的问题进 Bug 单，派生修复任务，按等级决定自动修还是等批 |
| 插件能力库 | 把 Kafka 读取、数据库工具等组件存成固定版本能力包，执行时直接交给 AI 复用 |
| 双通道通知 | 邮件为基座，微信/飞书推送为增强，通道失败逐级降级 |

## 决策分级

| 等级 | 范围 | 处理方式 |
|---|---|---|
| `L0` | 改文案、加日志、补注释、加测试、`docs/**` | AI 自主决定，只记录 |
| `L1` | 命名、内部结构、小版本依赖、错误提示措辞 | AI 自主决定，进日报 |
| `L2` | 接口签名变更、DB 字段增减、跨模块改动、新增依赖 | **挂起，推送等你批** |
| `L3` | 删数据/删表、权限鉴权、上线发版、打包流程、密钥证书 | **挂起，邮件 + 手机强提醒** |

## 文档

| 文档 | 内容 |
|---|---|
| `TECH_STACK.md` | 技术选型、Flyway 规范、配置约定 |
| `ARCHITECTURE.md` | 限界上下文、聚合边界、分包规范、反模式 |
| `FRONTEND.md` | 前端 UI 框架、macOS 设计令牌、组件清单 |
| `AGENTS.md` | 硬约束与工作流，**改代码前必读** |

## 快速开始

```bash
./deploy/local/start-all.sh     # 起后端 + 前端（已配置时再起公网中继连接器）
./deploy/local/status.sh        # 看状态
./deploy/local/stop-all.sh      # 停
```

- 前端 <http://127.0.0.1:12580/>
- 后端 <http://127.0.0.1:8787/actuator/health>
- 日志 `~/.regent/logs/`（pid 与日志都写 `~/.regent/`，不落仓库）

需要 JDK 17、Maven 3.9+、Node 20+、本机 MySQL（`regent` / `regent_test` 两个 schema，
凭据写在已被 gitignore 的 `regent-boot/src/main/resources/application-local.yml`）。
邮箱注册还需要在同一个文件里配置 `spring.mail.*` 的 SMTP 发件账号和授权码；也可以用
`SPRING_MAIL_HOST`、`SPRING_MAIL_PORT`、`SPRING_MAIL_USERNAME`、`SPRING_MAIL_PASSWORD`
环境变量覆盖。验证码 5 分钟有效、60 秒内不可重发、同一邮箱每天最多 10 次。
演示数据**默认不加载**（2026-10-16 起）：本地库是干净的，界面上出现的都是真实录入的数据。
想灌回那套演示数据（45 条任务、10 张决策单，分页开箱三页可翻），按 `application-local.yml`
注释里的说明把 `flyway.locations` 加回 `classpath:db/seed` 再重启即可；脚本是幂等的，
不想要时执行它开头「第 0 段」的 DELETE 就能清掉。**演示数据不进生产** ——
生产 profile 的 `flyway.locations` 里从来没有这一行。

> **本地默认关掉派发心跳**（`application-local.yml` 里的 `regent.execution.dispatch.enabled: false`）。
> 演示数据里的项目路径是真实仓库（`app_audit`、Regent 自己），一旦你把演示数据灌回来，
> 心跳一开，起个服务就会真的跑任务 —— 烧 token、切分支、改文件。要看自动派发就改成 `true`；
> 只验证单条任务，用界面上的「派发给 codex」或 `POST /api/tasks/{id}/dispatch`。

手机公网访问见 `doc/设计文档/移动端公网中继.md` 与 `deploy/README.md`：
公网服务器只运行无状态中继和静态前端，本机连接器主动长轮询服务器；
Regent、MySQL、codex 和项目工作区始终留在本机。
一次配对：`./deploy/remote/deploy.sh` 会自动写好本机 `~/.regent/relay-connector.env`
并探活连接器；想让手机随时打得开，再跑 `./deploy/local/relay-agent.sh install` 交给 launchd 常驻。

## 让别的工具自己上去找活干

平台**不接任何模型**。它只公布一个地址：执行者自己去读协议、领活、干活、把结果报回来 ——
跑活的是你手里那个 codex（或别的 CLI、别的模型工具），平台全程不知道它是谁。

```bash
curl -s http://127.0.0.1:8787/api/agent/contract.md   # 协议全文，读一次就够
```

把下面这段话连同**地址和项目**丢给你的 codex 就行：

> 你是 Regent 平台的外部执行者。先读 `http://127.0.0.1:8787/api/agent/contract.md`，
> 然后按协议在项目 `regent` 上找活干：看清单 → 认领 → 在 `workdir` 里、`branch` 上干完 → 上报。
> 遇到要拍板的地方如实报 `BLOCKED`，不要自己放宽分级。

平台这一侧只有三件事要留意：

- **想让它独占派发，把 `regent.execution.dispatch.enabled` 关掉** —— 内部心跳开着时，
  两边会抢同一批待执行任务；
- **执行者要与后端同机**：工作区只有一份，切分支由平台负责；换台机器得先有按任务隔离的工作区；
- **认领即拿租约**，默认 900 秒不心跳就按失联处理（`regent.agent.lease-seconds`），
  回收由 `AgentLeaseJob` 负责，任务会回到可以重试的状态。

平台同时运行的任务数由工具栏右侧的「执行设置」控制，范围 1–64。内部 `codex exec`
和外部执行者共用同一个闸门；调低上限不会打断正在跑的任务，只会让新任务等待空位。

任务详情里的运行记录会标出这次是谁在跑：本机 codex，还是「外部执行者 · 它的自称」。

## 目录

```
regent-commons/         共享内核
regent-core/            领域层 + 应用层（纯 Java，零框架依赖）
regent-infrastructure/  持久化、codex 适配、通知实现、Flyway 脚本
regent-interfaces/      REST、调度入口、CLI、DTO
regent-boot/            Spring Boot 启动与装配
regent-relay/           公网中继与本地连接器（零第三方运行时依赖）
regent-web/             前端（Vue 3 + Tailwind v4，macOS 观感）
doc/                    需求 / 设计 / 开发 / 数据库 / 测试 / bug
deploy/                 本地启停脚本 + 远程发布脚本
```

## 当前状态

**执行链路已经真跑通**：录入需求 → 派发 `codex exec` → JSONL 事件流落库 → 出参翻译 →
遇决策按 L0–L3 分级 → 阈值内自主决断并立刻续跑 / 超阈值挂起等人 → 批准后 `codex exec resume`
带结论续跑 → 收敛为终态。2026-10-16 用真实 codex CLI 在临时仓库上端到端验证过一轮
（改文件、出参合规、token 与事件流全部落库，见 `ARCHITECTURE.md` 第十一节）。

- 领域层：`Task` 聚合（带守卫的状态机）、`TaskRun` 聚合（一次尝试一行不可改写的事实）、
  决策分级仲裁 `DecisionArbiter`（规则从 `decision_rule` 表加载，解析失败即报错不跳过）
- 应用层：`TaskRunService`（一次尝试的完整生命周期，含自动决断循环与人工批准后的续跑）、
  `TaskDispatchService`（认领 + 后台执行，并发上限与失败冷却）、`ExecutionRecoveryService`
  （启动时收敛重启遗留的执行并把任务放回队列）、`DecisionRaiseService`（分级入口）
- 基础设施：`CodexCliAdapter`（进程、超时、stdout/stderr 分流、JSONL、末条消息）、
  `GitBranchManager`（每个任务一条分支，绝不直接改主干）、MyBatis 仓储
- 接口：`GET /api/tasks`（支持 `status` / `projectId` / `keyword` / 分页）、`POST /api/tasks`（人工录入）、
  `PUT /api/tasks/{id}`（编辑指令与优先级，已完成任务拒绝）、
  `/api/tasks/stats`、`GET /api/tasks/{id}`、
  `POST /api/tasks/{id}/retry|cancel|dispatch`、`GET /api/tasks/{id}/runs`、`GET /api/runs/{id}`、
  `POST /api/tasks/{id}/attachments`（贴图，`multipart/form-data`）、`GET /api/tasks/{id}/attachments`、
  `GET /api/attachments/{id}/content`（原图）、
  `GET /api/projects`、`POST /api/projects`（注册项目）、
  `GET /api/projects/directories`（列本机目录，给注册表单的路径选择器用）、
  `GET|PUT /api/execution/settings`（读取 / 修改最大同时运行任务数）、
  `POST /api/auth/email-codes`（发送邮箱注册验证码）、`POST /api/auth/register`（邮箱验证码注册）、
  `POST /api/auth/login|logout`、`GET /api/auth/me`（会话认证，登录支持邮箱或旧用户名；
  `rememberMe` 开启 180 天持久登录，剩余有效期过半后自动顺延）、
  `GET /api/decisions`、`POST /api/decisions/{id}/approve|reject`、
  `GET|POST /api/plugins`、`PUT /api/plugins/{id}`、`PUT /api/plugins/{id}/enabled`（插件库与版本）、
  `GET|PUT /api/tasks/{id}/plugins`（任务固定版本绑定）、
  `GET /api/bugs`（支持范围 / 严重级 / 关键词 / 分页）、`POST /api/bugs`（人工录入）、
  `POST /api/bugs/{id}/fix-task`（创建并派发修复任务、回填修复任务号）、
  **外部执行者**：`GET /api/agent/contract(.md)`（协议全文 + 出参 schema）、
  `GET /api/agent/tasks?project=…`（可领清单）、`POST /api/agent/tasks/claim`、
  `POST /api/agent/runs/{id}/heartbeat`、`POST /api/agent/runs/{id}/report`
- 前端：任务列表 / 分页 / 筛选 / 搜索 / 检查器、**新建任务弹框（贴图 + 拖放）与任务编辑弹框**、
  **项目管理页**（项目画像 + 最近任务）与「注册项目」（入口在工具栏项目下拉右边、项目页、
  以及一个项目都没有时的新建任务弹框里；路径能手打、也能从本机目录树里选）、按项目筛选、决策审批闭环、
  **运行记录面板与事件流弹框**、**详情里的附件缩略图与看图弹框**、**执行设置弹框**、
  **插件库与任务插件绑定**
- 测试：`regent-core` 单测（毫秒级反馈回路）+ 适配器 11 个 + 26 条集成测试
  （真 MySQL、真 Git、假 codex 脚本回放 JSONL）+ 7 条 ArchUnit 架构守卫

**还没做**：决策通知的邮件/推送通道与邮件里的「一键批准」入口、需求单本身与「需求 → 任务」的拆解链路、
Bug 单的标记已修复 / 验证关闭 / 不修复状态流转、决策规则的维护界面、
定时心跳的运维开关（当前只有配置文件一个入口）。
待办见 `ARCHITECTURE.md` 第十一节。

任务可以由人直接录入（`POST /api/tasks`，来源 `MANUAL`），也可以由需求拆解而来（来源 `REQUIREMENT`，
拆解链路未做）。两者都挂在项目下：项目决定 codex 在哪个目录跑、以及多高的决策必须惊动人。

## 前置条件

- JDK 17、Maven 3.9+
- 7x24 可用的 MySQL 实例（本机 8.0.43 已就绪），以及 `regent` / `regent_test` 两个 schema
- 可用的 `codex` CLI

> ⚠️ 数据库是这套平台的单点依赖：**DB 挂了，整条自动化就停摆**。
