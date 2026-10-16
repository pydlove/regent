# Regent 项目约定

> 本项目由 Codex 分多次会话开发。本文件是**强制性约定**，每次动手前必须先读。
> 完整设计见 `ARCHITECTURE.md`，技术选型见 `TECH_STACK.md`。

## 项目定位

多项目需求自动执行平台，带分级决策升级。核心逻辑：录入需求 → 调度 `codex exec` 执行 →
遇到需要决策的地方按 L0–L3 分级 → L0/L1 由 AI 自主决断并留痕，L2/L3 挂起并邮件/推送通知人工审批 →
批准后 `codex exec resume` 断点续跑 → 完成标记状态，过程中发现的缺陷进 Bug 单。

## 语言

默认使用简体中文：说明、注释、文档、提交信息、代码评审意见一律中文。
代码、命令、路径、日志、错误信息、API 字段名、包名类名保持原样，不翻译。

## 必读文档的优先级

1. `ARCHITECTURE.md` — 限界上下文、聚合边界、分包规范、反模式清单
2. `TECH_STACK.md` — 技术选型、Flyway 规范、配置约定
3. 本文件 — 硬约束与工作流

三者冲突时，以本文件的硬约束为准；本文件未覆盖的，以 `ARCHITECTURE.md` 为准。

## 构建与验证

```bash
mvn -q clean verify                    # 全量构建 + 全部测试
mvn -q -pl regent-core test            # 只跑核心域单测（最快的反馈回路）
mvn -q -pl regent-boot spring-boot:run # 本地启动
```

> 待 `pom.xml` 落地后以上命令才可用。

- **改领域逻辑必须先跑 `regent-core` 的单测**，那是毫秒级反馈。不要一上来跑全量构建。
- 提交前 `mvn -q clean verify` 必须全绿。
- 集成测试需要 MySQL；若本机没有可用实例，**如实说明跑不了**，不要伪造通过。

## 硬约束：架构纪律

以下每一条都是编译期或测试可验证的。违反即为错误，不接受"先这样以后再改"。

### 1. `regent-core` 零框架依赖

`regent-core` 下的任何类**禁止**出现这些 import：

```
org.springframework.*      org.apache.ibatis.*
com.fasterxml.jackson.*    jakarta.servlet.*
javax.persistence.*        org.slf4j.*
```

它必须是纯 Java，能用裸 JUnit 跑起来。Spring 装配一律写在 `regent-boot/config` 下，用
`@Configuration` + `@Bean` 显式声明，**不许把 `@Component`/`@Service` 打进 `regent-core`**。

### 2. 依赖方向单向

`commons ← core ← infrastructure / interfaces ← boot`。反向依赖一律禁止。
`domain` 层禁止依赖同上下文或任何上下文的 `infrastructure`。

### 3. 聚合根之间只许持 ID

```java
// 正确
private final RequirementId requirementId;

// 禁止
private Requirement requirement;
```

跨聚合的状态变更必须由**应用服务显式编排**，不允许在某个聚合的方法里反手改另一个聚合。

### 4. 状态迁移只能走领域方法

禁止 `setStatus(...)`、禁止用 MyBatis 直接 UPDATE 状态字段。状态迁移必须封装成带守卫的领域方法：

```java
public void block(DecisionId decisionId, DecisionLevel level) {
    requireState(TaskStatus.RUNNING, "只有执行中的任务可以阻塞");
    this.status = TaskStatus.BLOCKED;
    registerEvent(new TaskBlocked(this.id, decisionId, level, Instant.now()));
}
```

### 5. 仓储接口在 domain，实现在 infrastructure

`TaskRepository` 定义在 `regent-core`，`MyBatisTaskRepository` 实现在 `regent-infrastructure`。
**MyBatis Mapper 绝不出现在 `domain` 层**，仓储层内部用 `TaskDataMapper` 做「领域对象 ↔ 数据对象」转换。
领域层不知道表名、不知道 SQL、不知道 MyBatis 存在。

### 6. DTO 与领域对象严格分离

对外契约只允许 `interfaces/dto` 下的 DTO。**禁止把领域对象直接序列化返回**——
领域对象带行为方法和不变量，一旦变成 JSON 契约就被锁死了。

### 7. 外部系统必须走端口 + 适配器

`codex` CLI、Git、邮件、推送通道，全部先定义端口接口（放 `domain/port/`），
实现在 `infrastructure`。外部系统的类型和输出格式**不许渗进 `regent-core`**。
`codex` 的 JSONL 解析与事件翻译全部收在 `infrastructure/execution/codex/` 包内。

### 8. 分层调用规则

| 层 | 可以调用 | 禁止 |
|---|---|---|
| `domain` | 同上下文 `domain`、`shared` | 任何框架、任何 `infrastructure` |
| `application` | 同上下文 `domain`、跨上下文的**仓储接口** | 直接调 Mapper、直接发 HTTP |
| `infrastructure` | `domain` 的接口（去实现它） | 被 `domain` 反向调用 |
| `interfaces` | 同上下文 `application` | 直接调仓储、返回领域对象 |

## 硬约束：数据库与 Flyway

1. **已执行过的 `V` 脚本永不修改** — 写错只能追加 `V<n+1>` 修正。改历史脚本会让 `validate` 在别的环境直接失败。
2. **`clean-disabled: true` 必须显式声明** — Flyway 的 `clean` 会删库。
3. **迁移脚本只放 `regent-infrastructure/src/main/resources/db/migration/`** — 不许放到 `doc/数据库文档/`。
   注意：`app_audit` 历来把 `app_audit_update_x.y.z.sql` 手工存放在 `doc/` 下，**这个习惯在本项目废止**，
   版本化 SQL 有两个来源必然导致 `flyway_schema_history` 与真实结构漂移。
4. **不引 JPA、不设 `ddl-auto`** — 建表来源只能有一个：Flyway。
5. **禁止手工改表结构** — 任何结构变更必须落成迁移脚本，走 Code Review。
6. **测试用独立 schema `regent_test`** — 永不允许测试指向开发库。

## 硬约束：决策分级（核心域）

`governance` 是本项目的**核心域**，是唯一不能出错的地方。

- 仲裁规则是「**就高不就低**」：AI 自评等级与规则命中等级取高者。AI 说 L0 而规则命中 `deploy/**`，实际按 L3 处理。
- 规则必须**保守**。宁可多问人工一次，也不要越权自动改。
- `DecisionArbiter` 的每一个分支都必须有单测，包括边界与冲突场景。
- 改动分级规则时必须同步补测试，缺测试的改动视为未完成。

## 代码风格

- 值对象**优先用 `record`**（Java 17 原生），不可变、自带值相等。
- 领域对象**禁止 `@Data`** — 它会生成 setter，直接助长贫血模型。领域对象只暴露带守卫的领域方法和只读 getter。
- Lombok 仅用于非领域层（DTO、数据对象、配置类）。
- 强类型 ID：`TaskId`、`DecisionId` 一律包成值对象，不用裸 `Long` 传递。
- 不写无意义注释。注释只解释「为什么」，不复述「做了什么」。

## 提交规范

提交信息格式：`type(scope): subject`

- `type`：`feat` / `fix` / `refactor` / `docs` / `test` / `build` / `chore`
- `scope`：限界上下文或模块名，如 `governance`、`execution`、`infrastructure`、`project`
- `subject` 用中文，动词开头

```
feat(governance): 实现决策分级仲裁就高不就低
fix(execution): 修正 codex JSONL 事件流的 thread_id 提取
docs(project): 补充顶层目录约定
```

## 工作流

- 探索阶段保持只读，除非明确要求实现。
- 改代码前先跑相关单测，改完再跑一遍。
- 发现与本任务无关的既有缺陷，**不要顺手修**，在结论里说明并建议单独开 Bug 单。
- 不新增与任务无关的抽象：不要给每个实体都造 `Factory` / `Builder` / `Specification` 全家桶，用不上就不写。

## 反模式速查（写之前过一遍）

- 贫血模型：`Task` 只有 getter/setter，业务逻辑堆在 `TaskService`
- 领域层出现 `org.springframework` 导入
- 聚合根持有另一个聚合根的对象引用
- 仓储返回 MyBatis 实体而不是领域对象
- 一个巨大的 `service` 包
- 把 `governance` 写薄：只硬编码几个关键词就算完
- 用领域事件做关键流转（阶段 1 不用；进程内事件没有投递保证）
