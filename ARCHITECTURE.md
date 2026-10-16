# Regent 架构与分包设计（DDD）

> 版本：v1.0
> 定义日期：2026-10-16
> 配套文档：`TECH_STACK.md`（技术栈）、`ARCHITECTURE.md`（本文档）
> 基础包名：`com.secsmart.regent`　groupId：`com.secsmart.regent`

---

## 一、战略设计：限界上下文与核心域

DDD 的第一步是回答「哪里值得投入最多精力」。这个平台的价值分布很不均匀：

| 限界上下文 | 域类型 | 职责 | 投入 |
|---|---|---|---|
| **`governance`** | **核心域** | 决策分级规则、仲裁「就高不就低」、决策单、审批令牌 | **最高**，唯一不能出错的地方 |
| `execution` | 支撑域 | 任务生命周期状态机、`codex exec` 调用、执行记录、事件流 | 高 |
| `requirement` | 支撑域 | 需求录入、拆解为任务 | 中 |
| `bug` | 支撑域 | 缺陷单、派生修复任务 | 中 |
| `project` | 支撑域 | 项目注册、项目画像（路径/验证方式/等级覆盖） | 中 |
| `notification` | 通用域 | 邮件与推送投递、通道降级 | 低，能跑就行 |
| `shared` | 共享内核 | 值对象、领域事件基类、异常、ID | 低，但必须稳定 |

**为什么 `governance` 是核心域**：整件事的成败全压在「AI 该自己拍板还是该问你」这一个判断上。判错了轻则白干一轮，重则自动改坏东西。其余所有上下文都只是把数据搬到这个判断面前的管道。所以决策分级规则要设计得最细、测得分最透、演进得最谨慎。

**为什么 `notification` 是通用域**：发邮件和调 webhook 是任何系统都要做的事，没有领域特殊性，用现成轮子实现到「能用」即可，不要在这里花心思设计抽象。

---

## 二、战术设计：聚合与聚合边界

### 2.1 聚合根清单

| 聚合根 | 上下文 | 说明 |
|---|---|---|
| `Project` | `project` | 项目画像，含决策等级覆盖配置 |
| `Requirement` | `requirement` | 需求，持有 `List<TaskId>` **弱引用** |
| `Task` | `execution` | 执行单元，含状态机与 `codex_thread_id` |
| `TaskRun` | `execution` | 单次执行记录，token 消耗、分支名、退出码 |
| `Decision` | `governance` | 决策单，含「谁做的决定」与依据规则 |
| `DecisionRule` | `governance` | 分级规则（路径通配 / 关键词 / 项目级覆盖） |
| `Bug` | `bug` | 缺陷单，可派生修复任务 |
| `Notification` | `notification` | 投递记录 |

### 2.2 三个关键边界决策

**① `Task` 必须独立成聚合，不能塞进 `Requirement`。**

`Task` 是长时间运行、被执行器高频改状态的对象，生命周期远长于 `Requirement`。若把 Task 作为 Requirement 聚合内的实体，则每次「把某个任务标记为 RUNNING」都要加载整棵需求树并锁住整个聚合——执行器并行跑任务时直接互相打架。所以 `Requirement` 只持有 `List<TaskId>`，跨聚合通过 ID 弱引用。

**② `Decision` 独立于 `Task`。**

一个 Task 可能多次阻塞、产生多条决策。`Decision` 有自己的生命周期（待批 → 已批 → 已执行）和「谁批准的」这类审计属性。挂在 Task 下会让 Task 表变成一张巨宽的宽表。

**③ 单聚合内强一致，跨聚合最终一致。**

聚合内用领域方法守卫不变量；跨聚合（如「决策批准后 Task 解除阻塞」）**必须由应用服务显式编排**，不允许在 `Decision` 的方法里反手改 `Task`。这条是 DDD 最常被破坏的纪律。

### 2.3 任务状态机（领域不变量所在）

```
PENDING ──dispatch──▶ RUNNING ──success──▶ DONE
                         │
                         ├──needDecision──▶ BLOCKED ──approved──▶ RUNNING (resume)
                         │                     │
                         │                     └──rejected──▶ CANCELLED
                         └──error──▶ FAILED ──retry──▶ PENDING
```

终态：`DONE`、`CANCELLED`。`FAILED` 可经人工重试回到 `PENDING`。

状态迁移**只能通过领域方法**发生，绝不允许 `task.setStatus(...)`：

```java
public class Task extends AggregateRoot<TaskId> {

    private TaskStatus status;

    /** 派发执行。只有 PENDING 可派发。 */
    public void dispatch(BranchName branch) {
        requireState(TaskStatus.PENDING, "只有待执行任务可以派发");
        this.status = TaskStatus.RUNNING;
        this.branch = branch;
        registerEvent(new TaskStarted(this.id, branch, Instant.now()));
    }

    /** 执行中阻塞，等待人工决策。 */
    public void block(DecisionId decisionId, DecisionLevel level) {
        requireState(TaskStatus.RUNNING, "只有执行中的任务可以阻塞");
        this.status = TaskStatus.BLOCKED;
        this.blockingDecisionId = decisionId;
        registerEvent(new TaskBlocked(this.id, decisionId, level, Instant.now()));
    }

    /** 决策归位后续跑。 */
    public void resume(DecisionOutcome outcome) {
        requireState(TaskStatus.BLOCKED, "只有阻塞中的任务可以续跑");
        this.status = TaskStatus.RUNNING;
        this.appendContext("决策结果：" + outcome.freeText());
    }
}
```

### 2.4 值对象

不可变、用值相等、自带校验。至少要有：

`DecisionLevel`（L0–L3）、`ProjectPath`、`BranchName`、`ThreadId`、`TokenUsage`、`Severity`、`Reversibility`、`BlastRadius`、`TaskId` / `DecisionId` / `BugId` 等强类型 ID。

强类型 ID 不是形式主义：`TaskId` 和 `DecisionId` 都是 `Long` 时，传错参数编译器一声不吭；包成值对象后这类错误在编译期就挂了。

---

## 三、Maven 模块划分与依赖方向

### 3.1 为什么模块切得「粗」

**这里有个真实取舍，先说清楚。** 教科书式的 DDD 会给每个限界上下文一个独立 Maven 模块（`regent-governance`、`regent-execution`、`regent-bug`…）。但本项目大约 150–200 个类、五千到八千行，切成十几个模块会立刻带来：

- 改一处业务逻辑要跳五六个 `pom.xml`
- 全量编译变慢，IDE 索引和重构变迟钝
- 上下文之间的协作要经过一层层 `public` 暴露和依赖声明，噪音盖过收益

**结论：Maven 模块只承担「物理边界」职责，限界上下文用包来承载。** 模块边界只有一个目的——**强制领域层不依赖框架**。这是 DDD 里投入产出比最高的一条编译期约束，其余边界交给包约定 + 架构测试守。

### 3.2 五个模块

| 模块 | 内容 | 允许依赖 | 禁用 |
|---|---|---|---|
| `regent-commons` | `shared.*` 共享内核 | 仅 JDK | 任何框架 |
| `regent-core` | **所有上下文的 `domain` + `application` 包** | `regent-commons` | **Spring、MyBatis、Jackson、HTTP 全部禁用** |
| `regent-infrastructure` | 所有上下文的 `infrastructure` 包、MyBatis XML、**Flyway 迁移脚本** | `regent-core` | — |
| `regent-interfaces` | 所有上下文的 `interfaces` 包、REST、调度入口、CLI | `regent-core` | — |
| `regent-boot` | `main`、Spring 装配、`application-*.yml` | 全部 | — |

`regent-core` 是整套设计的腰眼：**它是纯 Java，可以脱离 Spring 和数据库直接用 JUnit 跑**。决策仲裁这种关键逻辑能在毫秒级单测里跑上百个用例，这是它必须纯净的唯一理由。

### 3.3 依赖方向

```mermaid
graph TD
    BOOT["regent-boot<br/>启动装配"] --> INT["regent-interfaces<br/>REST / 调度 / CLI"]
    BOOT --> INF["regent-infrastructure<br/>持久化 / codex / 通知"]
    BOOT --> CORE["regent-core<br/>domain + application"]
    INT --> CORE
    INF --> CORE
    CORE --> COM["regent-commons<br/>共享内核"]
    INF -.->|"Flyway 迁移脚本"| DB[("MySQL<br/>schema regent")]
```

箭头只许向下，**不许出现反向依赖**。`regent-core` 不认识 Spring、不认识 MySQL、不认识 `codex` 命令，它只认自己定义的端口接口。

### 3.4 什么时候该把上下文升级成独立模块

记住这个判据，别提前优化：

- 需要**独立部署**（比如通知服务要单独横向扩）
- 需要**编译期禁止**两个上下文互相依赖（而不是靠自觉）
- 某上下文的依赖集与其他上下文**严重冲突**（比如引入了不同大版本的库）

三条都不满足，就继续用包。

---

## 四、目录结构

### 4.1 顶层目录（`regent/` 下）

```
regent/
├── .gitignore
├── .editorconfig
├── AGENTS.md                    # 给 Codex 的项目约定（本仓库由 Codex 开发，待写）
├── README.md                    # 待写
├── TECH_STACK.md                # 技术栈定义
├── ARCHITECTURE.md              # 本文档
├── pom.xml                      # 聚合 POM，<parent> 指向 api-parent
│
├── regent-commons/              # ┐
├── regent-core/                 # │
├── regent-infrastructure/       # ├─ 五个 Maven 模块，内部结构见 4.2
├── regent-interfaces/           # │
├── regent-boot/                 # ┘
├── regent-web/                  # 前端工程，独立于 Maven（待定 Vue/React）
│
├── doc/                         # 项目文档，集中于根目录
│   ├── 需求文档/
│   ├── 设计文档/
│   ├── 开发文档/
│   ├── 数据库文档/
│   ├── 测试文档/
│   └── bug/
├── openspec/                    # 规格驱动开发产物
│   ├── config.yaml
│   ├── specs/
│   └── changes/
├── deploy/                      # 运行守护：start-all / stop-all / restart / status
└── scripts/                     # 一次性运维与开发脚本
```

| 顶层项 | 职责 | 对齐来源 |
|---|---|---|
| `regent-*`（五个） | Maven 模块，唯一构建单元 | `app_audit` 的多模块聚合 |
| `regent-web` | 前端 npm 工程，**不参与 Maven 构建** | `app-audit-frontend` 的独立工程形态 |
| `doc/` | 需求 / 设计 / 开发 / 数据库 / 测试 / bug 六类文档集中存放 | `app_audit/doc/` 同名同构 |
| `openspec/` | 规格驱动开发产物 | `app_audit`、`safe-guard`、`test_maf` |
| `deploy/` | 运行守护脚本 | `py-deploy` 的 start/stop/status 习惯 |
| `scripts/` | 一次性脚本，不进构建 | `nexis`、`test_maf` |

三条摆放规矩：

- **文档不散落在模块内。** 所有设计、需求、bug 记录集中在根目录 `doc/`，模块目录里只放代码和资源。跨模块评审要在同一个地方看得到全貌。
- **运行时数据一律不落仓库。** 日志、执行记录、本地配置全在 `~/.regent/`。**仓库里出现 `logs/` 或 `*.log` 即为配置错误**，`.gitignore` 已兜底，但那是安全网不是借口。
- **Flyway 迁移脚本的唯一位置是 `regent-infrastructure/src/main/resources/db/migration/`**，不是 `doc/数据库文档/`。

> **这里有一条需要刻意改变的旧习惯。** `app_audit` 历来把 `app_audit_update_3.2.5.sql` 这类升级脚本手工存放在 `doc/` 下维护。引入 Flyway 后**这个做法必须废止**：版本化 SQL 一旦有两个来源，`flyway_schema_history` 与真实表结构就会漂移，而漂移不会当场报错，只会在某个环境上突然炸。`doc/数据库文档/` 只放说明性内容（ER 图、字段解释、变更说明），不放可执行脚本。

### 4.2 模块与包结构

```
regent/
├── pom.xml                                   # 聚合 POM，<parent> 指向 api-parent
├── README.md
├── TECH_STACK.md
├── ARCHITECTURE.md
│
├── regent-commons/
│   └── src/main/java/com/secsmart/regent/shared/
│       ├── domain/                           # 基础构件
│       │   ├── AggregateRoot.java
│       │   ├── Entity.java
│       │   ├── ValueObject.java
│       │   ├── DomainEvent.java
│       │   ├── DomainException.java
│       │   └── Invariant.java
│       ├── kernel/                           # 通用值对象
│       │   ├── DecisionLevel.java            # L0–L3，核心域概念
│       │   ├── Severity.java
│       │   ├── ProjectPath.java
│       │   ├── BranchName.java
│       │   ├── ThreadId.java
│       │   └── TokenUsage.java
│       └── util/
│
├── regent-core/                              # 纯 Java，零框架依赖
│   └── src/main/java/com/secsmart/regent/
│       ├── project/
│       │   ├── domain/
│       │   │   ├── model/                    # Project(AR), VerifyMethod, TechStack
│       │   │   ├── repository/               # ProjectRepository（接口）
│       │   │   └── service/                  # ProjectRegistrar
│       │   └── application/
│       │       ├── command/                  # RegisterProjectCommand
│       │       ├── ProjectApplicationService.java
│       │       └── query/                    # ProjectQueryService
│       │
│       ├── requirement/
│       │   ├── domain/
│       │   │   ├── model/                    # Requirement(AR), RequirementStatus, TaskPlan
│       │   │   ├── repository/
│       │   │   ├── service/                  # RequirementDecomposer
│       │   │   └── event/                    # RequirementSubmitted, RequirementPlanned
│       │   └── application/
│       │
│       ├── execution/
│       │   ├── domain/
│       │   │   ├── model/                    # Task(AR), TaskStatus, TaskRun, RunEvent
│       │   │   ├── repository/               # TaskRepository, TaskRunRepository
│       │   │   ├── service/                  # TaskStateMachine
│       │   │   ├── port/                     # AgentExecutor, BranchManager  ← 端口
│       │   │   └── event/                    # TaskQueued/Started/Blocked/Completed/Failed
│       │   └── application/
│       │       ├── TaskDispatchService.java  # 取任务、派发、并发闸门
│       │       ├── TaskRunService.java       # 单任务生命周期编排
│       │       └── query/
│       │
│       ├── governance/                       # ★ 核心域
│       │   ├── domain/
│       │   │   ├── model/                    # Decision(AR), DecisionRule(AR),
│       │   │   │                             # ApprovalToken, DecisionOutcome, EscalationPolicy
│       │   │   ├── repository/
│       │   │   ├── service/                  # DecisionArbiter ← 就高不就低
│       │   │   └── event/                    # DecisionRaised/AutoResolved/Approved/Rejected
│       │   └── application/
│       │       ├── DecisionRaiseService.java
│       │       ├── DecisionApprovalService.java
│       │       └── RuleAdminService.java
│       │
│       ├── bug/
│       │   ├── domain/
│       │   │   ├── model/                    # Bug(AR), BugStatus, Reproduction
│       │   │   ├── repository/
│       │   │   └── event/                    # BugReported, BugFixTaskRequested
│       │   └── application/
│       │
│       └── notification/
│           ├── domain/
│           │   ├── model/                    # Notification(AR), Channel, DeliveryStatus
│           │   ├── repository/
│           │   ├── service/                  # NotificationRouter（通道降级）
│           │   └── port/                     # NotificationSender ← 端口
│           └── application/
│
├── regent-infrastructure/
│   └── src/main/
│       ├── java/com/secsmart/regent/
│       │   ├── project/infrastructure/persistence/     # MyBatisProjectRepository, ProjectMapper
│       │   ├── requirement/infrastructure/persistence/
│       │   ├── execution/
│       │   │   ├── codex/                    # ★ 防腐层
│       │   │   │   ├── CodexCliAdapter.java        # 实现 AgentExecutor
│       │   │   │   ├── CodexJsonlParser.java       # 逐行解析事件流
│       │   │   │   ├── CodexEventTranslator.java   # 外部事件 → 领域事件
│       │   │   │   └── SchemaFileProvider.java     # 提供 --output-schema 文件
│       │   │   ├── git/                      # GitBranchAdapter（实现 BranchManager）
│       │   │   └── persistence/
│       │   ├── governance/infrastructure/persistence/
│       │   ├── bug/infrastructure/persistence/
│       │   └── notification/infrastructure/
│       │       ├── email/                    # SmtpNotificationSender
│       │       ├── push/                     # FeishuSender / ServerChanSender / DingTalkSender
│       │       └── persistence/
│       └── resources/
│           ├── db/migration/                 # ★ Flyway
│           │   └── V1__init_schema.sql
│           └── mapper/                       # MyBatis XML
│
├── regent-interfaces/
│   └── src/main/java/com/secsmart/regent/
│       ├── project/interfaces/rest/          # ProjectController
│       ├── requirement/interfaces/rest/
│       ├── execution/interfaces/
│       │   ├── rest/                         # TaskController
│       │   └── scheduler/                    # TaskDispatchJob（@Scheduled）
│       ├── governance/interfaces/rest/       # DecisionController, ApprovalLinkController
│       ├── bug/interfaces/rest/
│       ├── notification/interfaces/
│       └── dto/                              # 各上下文的 DTO（对应 app_audit 的 to 模块）
│
└── regent-boot/
    └── src/main/
        ├── java/com/secsmart/regent/boot/
        │   ├── RegentApplication.java
        │   └── config/                       # 显式装配 Bean
        └── resources/
            ├── application.yml
            ├── application-dev.yml
            └── application-test.yml
```

---

## 五、分包规范

### 5.1 每个上下文固定四层

```
<context>/
├── domain/          领域层：模型、领域服务、仓储接口、端口、领域事件
├── application/     应用层：用例编排、命令、查询
├── infrastructure/  基础设施层：仓储实现、外部系统适配器
└── interfaces/      接口层：REST、调度入口、DTO
```

上下文内**必须**遵循这个四层；上下文之间**不出现** `domain` 直接引用别的上下文 `domain`：

```java
// 允许：应用层跨上下文编排
@Service
public class DecisionApprovalService {
    private final TaskRepository taskRepository;        // 别的上下文的仓储接口
    private final DecisionRepository decisionRepository;
}

// 禁止：Bug 聚合根里直接操作 Requirement 聚合
public class Bug {
    private Requirement requirement;   // ← 绝对不允许持有别的聚合根对象
    private RequirementId requirementId;  // ← 只允许持有 ID 弱引用
}
```

### 5.2 层间调用规则

| 层 | 可以调用 | 禁止 |
|---|---|---|
| `domain` | 同上下文 `domain`、`shared` | 任何框架、任何 `infrastructure`、任何装配 |
| `application` | 同上下文 `domain`、`shared`、跨上下文 `domain` 的**仓储接口** | 直接调 Mapper、直接发 HTTP |
| `infrastructure` | 同上下文 `domain` 的接口（去实现它） | 反向被 `domain` 调用 |
| `interfaces` | 同上下文 `application` | 直接调仓储、返回领域对象 |

### 5.3 仓储接口在 domain，实现在 infrastructure

这条最常做错，单独强调：

```java
// regent-core/execution/domain/repository/TaskRepository.java
public interface TaskRepository {          // ← 领域层定义，纯 Java
    Optional<Task> findById(TaskId id);
    void save(Task task);
    List<Task> findDispatchable(int limit);
}

// regent-infrastructure/.../MyBatisTaskRepository.java
@Repository
public class MyBatisTaskRepository implements TaskRepository {
    private final TaskMapper taskMapper;   // ← MyBatis 类型只在基础设施层出现
    private final TaskDataMapper dataMapper;  // 领域对象 ↔ 数据对象 转换
}
```

**MyBatis Mapper 绝不作为仓储暴露给领域层。** 领域层只认 `TaskRepository`，不知道 MyBatis、不知道表名、不知道 SQL。这样换存储只改一个实现类。

### 5.4 DTO 与领域对象严格分离

`interfaces/dto` 下的 DTO 才是对外契约，**禁止把领域对象直接序列化返回**。理由是领域对象有行为方法和不变量，一旦成为 JSON 契约就被锁死了——改个字段名等于破坏对外接口。

对应你 `app_audit` 的 `to` 模块，同一套思路。

---

## 六、防腐层与端口

外部系统必须以**端口 + 适配器**接入，external 的类型不许渗进领域层。

**端口（`domain/port/`，纯接口）**
```java
public interface AgentExecutor {
    ExecResult execute(ExecRequest request);
    ExecResult resume(ThreadId threadId, String instruction);
}

public interface BranchManager {
    BranchName createFor(TaskId taskId);
}

public interface NotificationSender {
    Channel channel();
    void send(NotificationMessage message);
}
```

**适配器（`infrastructure/`，实现端口）**
`CodexCliAdapter` 实现 `AgentExecutor`，内部用 `ProcessBuilder` 拉起 `codex exec`；`CodexJsonlParser` 逐行解析 JSONL；`CodexEventTranslator` 把 `codex` 的事件模型翻译成领域事件。这三层合起来就是**防腐层**——`codex` 的输出格式哪天变了，改动全部收在这个包内，`regent-core` 一行不动。

`notification` 的 `push/` 包同理：飞书、Server酱、钉钉各一个 `NotificationSender` 实现，`NotificationRouter` 按配置和失败情况做降级。

---

## 七、跨上下文协作：阶段 1 不用领域事件

这是我最想让你注意的一条务实建议。

DDD 教程会说「上下文之间通过领域事件解耦」。听着优雅，但**进程内 Spring 事件没有投递保证**：`RequirementPlanned` 发出去之后进程崩了，任务就永远建不出来，而且没有任何地方记录它曾经该被建出来。要做到可靠就得引 transactional outbox 表 + 轮询投递，那是额外一整套机制。

对每天几十个任务、单进程、单库的场景，**用应用服务显式编排更简单也更可靠**：

```java
@Service
public class RequirementApplicationService {

    @Transactional
    public PlanResult submitAndPlan(SubmitRequirementCommand cmd) {
        Requirement requirement = Requirement.submit(cmd.projectId(), cmd.text());
        requirementRepository.save(requirement);

        List<TaskPlan> plans = decomposer.decompose(requirement);   // 领域服务
        for (TaskPlan plan : plans) {
            Task task = Task.fromPlan(requirement.id(), plan);       // 显式建 Task
            taskRepository.save(task);
            requirement.attachTask(task.id());                      // 只回填 ID
        }
        requirementRepository.save(requirement);
        return PlanResult.of(requirement, plans);
    }
}
```

一个事务、一眼看全、断点可断、挂了能重试。

**领域事件只用于旁路**：记日志、写审计、触发通知。这类丢了不致命。等哪天真出现「跨进程协作」或「投递必须可靠」的需求，再上 outbox。

---

## 八、架构守卫：用 ArchUnit 把规则变成测试

包约定靠自觉是守不住的。把上面的规矩写成可执行的测试，违规直接让 `mvn verify` 挂掉：

```java
@AnalyzeClasses(packages = "com.secsmart.regent")
class ArchitectureTest {

    static final ArchRule DOMAIN_MUST_BE_FRAMEWORK_FREE =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "org.apache.ibatis..",
                                "com.fasterxml.jackson..", "jakarta.servlet..");

    static final ArchRule INFRASTRUCTURE_MUST_NOT_BE_DEPENDED_ON_BY_DOMAIN =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    static final ArchRule AGGREGATES_MUST_NOT_HOLD_OTHER_AGGREGATES =
        noClasses().that().haveSimpleNameEndingWith("Repository")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    static final ArchRule CONTROLLERS_MUST_NOT_TOUCH_REPOSITORIES =
        noClasses().that().resideInAPackage("..interfaces..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository");
}
```

这四条规则的价值高于本文档其余所有内容的总和——**文档会过期，测试不会**。

---

## 九、反模式清单

写代码前过一遍，这些是最容易犯且后期最难改的：

- **贫血模型** — `Task` 只有 getter/setter，业务逻辑全堆在 `TaskService` 里。那样 DDD 就白设计了，直接退化成 MVC 三层。不变量和状态迁移必须待在聚合根里。
- **Spring 注解污染领域层** — 给 `Task` 打 `@Component`、给仓储实现打 `@Service` 塞进 domain 包。`regent-core` 里出现任何 `org.springframework` 导入都是错的，用 `@Configuration` 在 boot 模块显式装配。
- **跨聚合根持有对象引用** — 见 5.1，只许持 ID。
- **仓储返回 MyBatis 实体** — 仓储应该返回领域对象。中间要有一层 `TaskDataMapper` 做转换，别图省事。
- **一个巨大的 `service` 包** — 按上下文分，`service` 只出现在自己的上下文里。
- **把 `governance` 写薄** — 这是核心域。规则引擎写得粗糙、只支持硬编码几个关键词，整个平台的可靠性就没了。这里值得多花三倍时间。
- **过度分层** — 不要给每个实体都造 `Factory`、`Builder`、`Specification`、`DomainEventPublisher` 全家桶。用不上就不写。

---

## 十、与你现有项目约定的一致性

| 你的既有约定 | 本项目对应 | 说明 |
|---|---|---|
| `app_audit` 的 `to` 模块 | `interfaces/dto` | 对外传输对象，独立于领域模型 |
| `app_audit` 的 `entity` / `dao` 模块 | `infrastructure/persistence` | 不独立成 Maven 模块，收在上下文内 |
| `app_audit` 的 `xxx-interface` / `xxx-impl` 拆分 | 本项目的端口 / 适配器 | 同样是「接口归上层、实现归下层」，只是按 DDD 命名 |
| `safe-guard` 的 `domain` / `application` / `infrastructure` / `ports` / `presentation` | 本项目的 `domain` / `application` / `infrastructure` / `domain/port` / `interfaces` | 词汇对齐，你换语言不用换脑子 |
| `safe-guard` 的 `tests/{unit,integration,contract}` | `regent-core` 单测 / `boot` 集成测试 | 契约测试留给 DTO↔Schema 一致性 |
| 父 POM `api_parent` | 聚合 POM 的 `<parent>` | 版本矩阵复用 |
| `py-deploy` 的 start/stop/status | `deploy/` 同构脚本 | 操作习惯不变 |

---

## 十一、下一步

1. `regent/` 内 `git init`，建聚合 `pom.xml` + 五个子模块 POM，`<parent>` 指向 `api-parent`
2. 建 MySQL 常驻实例与 `regent` / `regent_test` 两个 schema
3. 写 `V1__init_schema.sql`（十张表），跑通 Flyway 首次迁移
4. 落地 `shared/domain` 基础构件 + `shared/kernel` 值对象（含 `DecisionLevel`）
5. 实现 `governance` 域：`DecisionArbiter` + 单测（先写这个，它是核心域）
6. 加 `ArchitectureTest` 四条规则
7. 实现 `execution` 域 + `CodexCliAdapter`，拿一个真实项目端到端试点

**待定项**（沿用 `TECH_STACK.md` 第十节）：前端 Vue / React 选型；MySQL 实例位置。
