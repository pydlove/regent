# Regent 技术栈定义

> 版本：v2.0（Java 版）
> 定义日期：2026-10-16
> 变更说明：v1.0 的 Python 选型已废弃。选型依据从「代码写得最少」改为「维护者优先」——
> 这套平台要长期无人值守运行，出问题时要读代码的人是潘勇，因此技术栈必须与维护者的主场一致。
> 一句话定位：多项目需求自动执行平台，带分级决策升级。

---

## 一、选型总原则

1. **维护者优先** — 谁维护谁选型。可读、可自修、可自主排障，压倒「代码量最少」。
2. **复用既有资产** — 继承 `api_parent`（Spring Boot 3.5.5 / Java 17 / MySQL 8.3 / Druid / JUnit5），不另立版本矩阵。
3. **Schema 单一事实源：Flyway** — 数据库结构只由版本化 SQL 演进，禁止手工改表，禁止任何 ORM 自动建表。
4. **契约先行** — 与 Codex 执行器之间的接口用 DTO 定义，DTO 生成 JSON Schema 供 `--output-schema` 使用，一致性由单测守住。
5. **安全默认保守** — 决策分级引擎是安全关键路径，必须有单测覆盖；数据库迁移必须可审、可回溯。

---

## 二、技术栈一览

| 层次 | 选型 | 版本 | 说明 |
|---|---|---|---|
| 语言 | Java | 17 | 与 `api_parent` / JDK 17.0.12 对齐 |
| 构建 | Maven | 3.9.11 | 本机已装 |
| 父 POM | `com.secsmart.api:api-parent` | 3.0.2_R100-SNAPSHOT | 依赖与插件版本统一在此 |
| 框架 | Spring Boot | 3.5.5 | 由父 POM 管理，**不要自己写版本号** |
| 持久层 | MyBatis-Plus `mybatis-plus-spring-boot3-starter` | 3.5.8，父 POM 管理 | 与 `app_audit` 保持一致，沿用其 dao 分层风格 |
| 连接池 | Druid | 1.2.24 | 父 POM 已有 |
| 数据库 | MySQL | 8.3.0 驱动 | 专用 schema `regent` |
| **迁移** | **Flyway** | 父 POM 管理（11.x） | `flyway-core` + `flyway-mysql` |
| JSON | Jackson | 父 POM 管理 | 逐行解析 `codex exec --json` 的 JSONL |
| JSON Schema | victools/jsonschema-generator | 4.x | 从 DTO 生成 Codex 出参 Schema |
| 进程调用 | `java.lang.ProcessBuilder` | JDK | 拉起 `codex exec` / `codex exec resume` |
| 调度 | Spring `@Scheduled` + `ThreadPoolTaskExecutor` | — | 不引 Quartz，现阶段用不上 |
| 邮件 | `spring-boot-starter-mail` | — | JavaMailSender |
| 推送 | Spring `RestClient` | — | Server酱 / PushPlus / 飞书 / 钉钉 webhook |
| 前端 | Vue 3 + Vite + TS + Tailwind CSS + reka-ui + Pinia | 见 `FRONTEND.md` | 对标 macOS 观感，**刻意不用 Element Plus** |
| 测试 | JUnit 5 + Mockito + Spring Boot Test | — | Testcontainers 需 Docker，本机没有，不采用 |
| 测试执行 | `maven-surefire-plugin` | **3.5.4，必须显式钉版本** | `api_parent` 未管理 surefire；Maven 默认 2.12.4 无法执行 JUnit 5，测试会被静默跳过且构建仍然「成功」 |
| 质量 | maven-surefire / maven-scm / 父 POM 插件链 | — | 复用父 POM 既有插件配置 |
| 守护 | 复用 `py-deploy` 的 start/stop/status 模式 | — | `java -jar` + 开机自启 |

---

## 三、数据库与 Flyway（核心）

### 3.1 为什么用 Flyway

- 用 MyBatis 意味着**没有 ORM 自动建表**，schema 必须有人管。Flyway 就是这个「人」。
- 迁移脚本进 Git，和业务代码同一次提交、同一次 Code Review、同一次回滚判断。**表结构变更从此可审计。**
- 决策日志、Bug 单这些表会随需求持续演进。没有 Flyway 的话，改表就是一场口头承诺，几周后没人记得生产库长什么样。
- 这条尤其重要：你这条产品线是安全审计类，`regent` 自己的表结构变更留痕，和产品本身的合规诉求是同一个逻辑。

### 3.2 依赖

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
    <scope>runtime</scope>
</dependency>
```

两个都必须有。Flyway 8.2 之后 **MySQL 支持被拆到独立的 `flyway-mysql` 模块**，只引 `flyway-core` 会在启动时报「不支持的数据库」。版本由父 POM 的 Spring Boot BOM 统一管理，**不要显式写版本号**。

### 3.3 目录与命名规范

```
regent-core/src/main/resources/db/migration/
├── V1__init_schema.sql              # 初始表结构（基线）
├── V2__add_decision_rule_table.sql
├── V3__add_task_run_thread_id.sql
└── R__view_task_board.sql           # 可重复执行：视图 / 只读查询
```

- 放在 `regent-core` 的 resources 下，随 jar 走，任何模块启动都能拿到。
- 版本化脚本 `V<序号>__<下划线描述>.sql`，序号连续、描述用下划线不用空格。
- 可重复脚本 `R__<描述>.sql` 用于视图和只读视图，内容变更时自动重跑。
- **绝不在 `V` 脚本里做应用逻辑**，它只演进结构。

### 3.4 配置

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
    clean-disabled: true        # 严禁 clean，防误删，务必显式声明
    out-of-order: false         # 版本号必须严格递增，防分支并行乱序
    table: flyway_schema_history
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/regent?useUnicode=true&characterEncoding=utf8mb4
    username: regent
    password: ${REGENT_DB_PASSWORD}
```

### 3.5 五条铁律

1. **已执行过的 `V` 脚本永不修改**。写错了只能追加 `V<n+1>` 修正，改动历史脚本会让 `validate` 在别的环境直接失败。
2. **`clean-disabled: true` 必须显式写**。Flyway 的 `clean` 会删库，默认虽已关闭，但别依赖默认值。
3. **专用 schema `regent` + 专用账号**，不要和 `app_audit` 等生产库共用。Flyway 需要 DDL 权限，必须把它的权限范围关在一个 schema 里。
4. **不引 JPA / 不设 `ddl-auto`**。MyBatis 时代没有 `ddl-auto`，但要明确禁止任何人为了图方便加进来——两套建表来源必然冲突。
5. **测试用独立 schema `regent_test`**，`test` profile 才允许 `clean-disabled: false`。永远不允许测试指向开发库。

### 3.6 初始 Schema 规划（`V1__init_schema.sql`）

| 表 | 职责 |
|---|---|
| `project` | 项目注册表：路径、技术栈、验证方式、决策等级覆盖 |
| `requirement` | 需求：原始描述、状态、所属项目 |
| `task` | 任务：由需求拆解或 Bug 派生，含 `codex_thread_id` |
| `task_run` | 每次执行的记录：开始/结束、退出码、token 消耗、分支名 |
| `run_event` | `codex exec --json` 的事件流落库，便于事后复盘 |
| `decision` | 决策：等级、问题、选项、AI 建议、**谁做的决定**、依据规则 |
| `decision_rule` | 分级规则：路径通配、关键词、项目级覆盖 |
| `bug` | Bug 单：严重级、复现步骤、来源任务、修复任务 |
| `notification` | 通知投递记录：通道、状态、重试次数 |
| `approval_token` | 一次性审批链接令牌 |

全部用 `utf8mb4`，主键用 `BIGINT`，状态字段用 `VARCHAR` 存枚举名（不用 MySQL `ENUM`，避免改枚举要动表结构）。时间统一 `DATETIME(3)` 存 UTC。

---

## 四、目录结构

```
regent/
├── pom.xml                      # 聚合 POM，<parent> 指向 api-parent
├── TECH_STACK.md
├── regent-api/                  # DTO / 对外接口定义
│   └── src/main/java/.../dto/   # CodexExecResult / DecisionDto / BugDto
├── regent-core/
│   ├── src/main/java/.../
│   │   ├── dao/                 # MyBatis Mapper 接口
│   │   ├── entity/
│   │   ├── service/
│   │   ├── policy/              # 决策分级：rules + arbiter（仲裁「就高不就低」）
│   │   └── notify/              # Notifier 抽象 + email/webhook 实现
│   └── src/main/resources/
│       ├── db/migration/        # ★ Flyway 迁移脚本
│       └── mapper/              # MyBatis XML
├── regent-executor/             # codex exec 封装、任务生命周期状态机、调度
├── regent-web/                  # 前端（Vue 3 + Tailwind，见 FRONTEND.md）
└── deploy/                      # start-all / stop-all / restart / status
```

---

## 五、Codex 出参契约

`regent-api` 中的 DTO 是唯一事实源，用 `victools/jsonschema-generator` 生成 Schema 文件：

```java
public record CodexExecResult(
    Status status,                 // DONE / BLOCKED / FAILED
    String summary,
    List<String> changedFiles,
    DecisionDto decision,
    List<BugDto> bugs,
    VerifyDto verify
) {}

public record DecisionDto(
    Level level,                   // L0 / L1 / L2 / L3
    String question,
    List<String> options,
    String recommendation,
    boolean reversible,
    String blastRadius
) {}
```

**一致性由单测守住**——这是 Java 相对 Python 多出来的那一步：

```java
@Test
void schemaMustMatchCommittedFile() {
    var generated = Generator.generate(CodexExecResult.class);
    var committed = read("codex-exec-result.schema.json");
    assertEquals(committed, generated,
        "DTO 改了但 schema 文件没更新，或反之");
}
```

提交后的 schema 文件供执行器调用：

```bash
codex exec \
  -C <项目目录> \
  -s workspace-write \
  --json \
  --output-schema src/main/resources/schema/codex-exec-result.schema.json \
  -o <末条消息输出文件> \
  "<注入的任务上下文 + 平台上报约定>"
```

`--json` 事件流里逐行取 `thread_id` 存进 `task.codex_thread_id`；决策批准后走 `codex exec resume <thread_id>` 断点续跑。

---

## 六、运行与部署

| 项 | 约定 |
|---|---|
| 启动 | `java -jar regent-boot.jar --spring.profiles.active=dev` |
| 监听 | `127.0.0.1:8787`（8440/8082/8899 已被占用，避开） |
| 数据库 | MySQL `127.0.0.1:3306`，schema `regent` |
| 数据目录 | `~/.regent/`（logs / runs / config） |
| 守护 | `deploy/` 下 start-all、stop-all、restart、status 四脚本 |
| 并发 | 单进程起步；多项目并行时按 project 维度加锁，避免同项目写冲突 |

> **前置条件**：本机当前没有 MySQL 服务（`mysqld` 不存在、3306 未监听、无 Docker，只有 homebrew `mysql-client`）。
> 选 MySQL 的代价就是它必须 7x24 可用——**DB 挂了整条自动化就停摆**。所以要装一个常驻实例并纳入守护脚本。

---

## 七、测试与质量

- **必须单测**：`policy/arbiter`（分级仲裁）、Codex JSONL 解析、DTO↔Schema 一致性、通知通道降级
- **集成测试**：用假 codex 脚本回放固定 JSONL，跑完整任务生命周期，不真调模型；DB 指向 `regent_test`
- **Flyway 测试**：`@SpringBootTest` 启动时自动在 `regent_test` 上跑全量迁移，顺便验证脚本可执行
- **门槛**：`mvn -q clean verify` 全绿

---

## 八、配置

`application-{profile}.yml` 为主，密码走环境变量，不进 Git：

```yaml
regent:
  policy:
    default-auto-level: L1          # 高于此等级一律升级人工
    escalate-patterns:
      - "deploy/**"
      - "**/*.sql"
      - "**/pom.xml"
    auto-patterns:
      - "docs/**"
      - "**/*.md"
  executor:
    max-concurrent-projects: 2
    daily-token-budget: 0           # 0 表示不限制
  notify:
    email:
      enabled: true
      host: ""
      port: 465
      username: ""
    push:
      enabled: true
      provider: feishu
      token: ""
```

---

## 九、风险与备选

| 风险 | 说明 | 应对 |
|---|---|---|
| MySQL 未常驻 | 本机没有 MySQL 服务，平台可用性从此绑定在 DB 上 | 先装常驻实例 + 纳入 start-all/status 脚本；冒烟时先探 DB |
| Flyway 误删库 | `clean` 会删整库 | `clean-disabled: true` 显式声明；专用 schema 限制爆炸半径 |
| 迁移脚本写错已执行 | 生产已跑过，改脚本会污染历史 | 铁律第 1 条：只追加不修改；发版前 review 脚本 |
| DTO 与 Schema 漂移 | Java 里两者天然是两份文件 | 一致性单测，漂移即失败 |
| `--output-schema` 被违反 | 模型未必每次严格合规 | 校验失败按 `FAILED` 处理并保留原始输出，绝不猜 |
| 无人值守被审批卡死 | headless 下不能出现交互式确认 | 每项目配 Codex `.rules` 白名单；危险命令一律判 `L3` |
| 测试基建 | Testcontainers 需 Docker，本机没有 | 走独立 `regent_test` schema 直连 |

---

## 十、待定项

1. ~~前端选型~~ **已定**：Vue 3 + Vite + Tailwind CSS + reka-ui，对标 macOS 原生观感，详见 `FRONTEND.md`。
   工作区现有前端（`app-audit-frontend`、`r_data_service/web`）**全是 React**，上 Vue 意味着将来维护两套前端规范——该代价已确认接受。
2. **MySQL 实例位置**：本机新装常驻实例，还是指向现有开发库？（决定守护脚本与 `V1__init_schema.sql` 的执行方式）
3. **工作区根 `.git` 干扰**：`regent/` 位于 `/Users/panyong/shanjie` 下，该根目录存在空 `.git`，会把 `regent/` 纳入其跟踪范围，建议在 `regent/` 内独立 `git init`。

---

## 十一、下一步

1. `regent/` 内 `git init`，建聚合 `pom.xml`，`<parent>` 指向 `api-parent`
2. 立 MySQL 常驻实例，建 `regent` / `regent_test` 两个 schema 与专用账号
3. 写 `V1__init_schema.sql`（十张表），跑通 Flyway 首次迁移
4. 落地 `regent-api` 的 DTO + victools 生成 Schema + 一致性单测
5. 实现 `policy/arbiter` 与单测
6. 打通 `regent-executor` + `notify`，拿一个真实项目做端到端试点
