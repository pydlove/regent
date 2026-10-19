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
| 数据库 | MySQL | 服务端 8.0.43 / 驱动 8.3.0 | 专用 schema `regent`，实例与账号见 3.7 |
| **迁移** | **Flyway** | 父 POM 管理（11.x） | `flyway-core` + `flyway-mysql` |
| JSON | Jackson | 父 POM 管理 | 逐行解析 `codex exec --json` 的 JSONL |
| JSON Schema | **手写契约文件** | — | `regent-infrastructure/src/main/resources/codex/` 下的 schema + 上报约定；victools 生成留作将来（见第五节） |
| 进程调用 | `java.lang.ProcessBuilder` | JDK | 拉起 `codex exec` / `codex exec resume`、本机测试脚本与 OpenSSH 部署 |
| 调度 | Spring `@Scheduled` + `ThreadPoolTaskExecutor` | — | 不引 Quartz，现阶段用不上 |
| 邮件 | `spring-boot-starter-mail` | — | JavaMailSender |
| 推送 | Spring `RestClient` | — | Server酱 / PushPlus / 飞书 / 钉钉 webhook |
| 前端 | Vue 3 + Vite + TS + Tailwind CSS + reka-ui + Pinia | 见 `FRONTEND.md` | 对标 macOS 观感，**刻意不用 Element Plus** |
| 测试 | JUnit 5 + Mockito + Spring Boot Test | — | Testcontainers 需 Docker，本机没有，不采用 |
| 测试执行 | `maven-surefire-plugin` | **3.5.4，必须显式钉版本** | `api_parent` 未管理 surefire；Maven 默认 2.12.4 无法执行 JUnit 5，测试会被静默跳过且构建仍然「成功」 |
| 质量 | maven-surefire / maven-scm / 父 POM 插件链 | — | 复用父 POM 既有插件配置 |
| 守护 | 复用 `py-deploy` 的 start/stop/status 模式 | — | `java -jar` + 开机自启 |
| 公网中继 | JDK `HttpServer` + `HttpClient` | Java 17 | 独立模块 `regent-relay`，无第三方运行时依赖；服务器无状态，本机主动长轮询 |

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
    url: jdbc:mysql://127.0.0.1:3306/regent?characterEncoding=UTF-8&connectionTimeZone=UTC&useSSL=false&allowPublicKeyRetrieval=true
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

`V3` 之后补进来的表在同名迁移脚本里，不混进 `V1`：

| 表 | 迁移 | 职责 |
|---|---|---|
| `task_attachment` | `V3__task_attachment.sql` | 任务图片附件：展示用文件名、`image/*` 类型、字节数、平台侧存储路径（磁盘文件名只有 UUID + 扩展名） |
| `bug_attachment` | `V8__bug_attachment.sql` | Bug 单图片附件：复现截图与现场证据，存储规则与任务附件一致，外键挂 `bug` |
| `environment_entry` | `V9__environment_entry.sql` | 任务环境信息：环境键由应用自动生成（`ENV_nnn`），用户填写 64 字名称；支持全局或项目归属、任意文本值、标签；项目同键值覆盖全局 |
| 环境名称与密码标记 | `V26__environment_entry_name_and_secret.sql` | 名称限制为 64 字；增加 `secret` 标记，密码值在列表与详情默认脱敏 |
| `relay_setting` | `V10__relay_settings.sql` | 中继服务器地址与状态监听开关；连接器共享密钥仍只保存在本机部署配置 |
| `relay_install_settings` | `V11__relay_install_settings.sql` | 中继安装的服务器 IP、SSH 用户名与安装目录；SSH 密码只在安装请求中使用，不落库 |
| `plugin` / `plugin_version` / `task_plugin` | `V17__plugin_library.sql`、`V18__plugin_builtin_key.sql` | 可复用能力包、不可变内容版本与任务固定版本绑定；`builtin_key` 标识随 JAR 发布的内置插件，启动时幂等同步，插件正文不保存凭据 |
| `test_case` / `task_test_case` / `test_case_run` | `V20__test_case_suite.sql` | Web / 接口测试用例、任务完成后的绑定，以及独立于 codex 执行记录的测试结果与输出 |
| `test_case_run` 手动执行字段 | `V21__manual_test_case_runs.sql` | 增加项目归属与 `trigger_type`，允许手动执行没有 `task_id` |
| `task_draft` | `V22__task_draft.sql` | 当前登录用户的单份任务录入草稿；保存项目、指令、优先级与插件 ID，不进入任务队列 |
| 项目资料字段 | `V23__project_summary.sql` | 保存 Codex 只读分析生成的 Markdown 项目资料、生成状态、失败原因与生成时间 |
| 部署配置与执行记录 | `V24__deployment.sql` | 保存服务器 SSH 配置、部署脚本与执行快照；密码不进入部署快照，日志写入 `~/.regent/deployments/runs/` |
| 部署归属项目 | `V25__deployment_project.sql` | 给部署配置与执行快照增加 `project_id`，部署创建后归属项目不可变更 |
| 部署密码密文 | `V27__deployment_credential.sql` | 独立保存 AES-GCM 加密后的 SSH 密码；主密钥只存在 `~/.regent/deployment/credential.key` |

全部用 `utf8mb4`，主键用 `BIGINT`，状态字段用 `VARCHAR` 存枚举名（不用 MySQL `ENUM`，避免改枚举要动表结构）。时间统一 `DATETIME(3)` 存 UTC。

### 3.7 本机环境与账号（已就绪）

2026-10-16 已在本机搭好并通过启动验证，可直接使用：

| 项 | 值 |
|---|---|
| 服务端 | MySQL 8.0.43，`127.0.0.1:3306`，数据目录 `/usr/local/mysql/data/` |
| Schema | `regent`（开发）、`regent_test`（测试），均 `utf8mb4` / `utf8mb4_0900_ai_ci` |
| 账号 | `regent`，密码见 `application-local.yml`（已 gitignore，不入库） |
| 权限 | `ALL` on `regent.*` 与 `regent_test.*`，外加 `SELECT` on `performance_schema.user_variables_by_thread` |

> **为什么不用 root。** 这台实例上还跑着 `quant_system`、`blogger_db`、`sinan_service_mgr` 等真实项目库。
> 而 Flyway 在**每次应用启动时**自动执行迁移且拥有 DDL 权限——用 root 意味着一个写错的迁移脚本
> 有能力删掉别的项目的库。专用账号把爆炸半径关在 `regent` schema 之内，已实测无法访问 `blogger_db`。
> root 只用于建库、授权这类管理动作。

**三个必踩的坑，配置里都已标注：**

1. **`characterEncoding` 必须是 Java 字符集名。** 写成 MySQL 的 `utf8mb4` 会抛
   `UnsupportedEncodingException: utf8mb4`，连接直接建立失败，而报错完全没提「字符集名写错了」。
   正确写法是 `characterEncoding=UTF-8`——Connector/J 8 下 UTF-8 即对应 utf8mb4。
2. **Flyway 需要 `performance_schema.user_variables_by_thread` 的 SELECT 权限。** 它在启动时做能力探测，
   缺权限会让应用启动直接失败，且报错极具误导性：`Unable to determine value for 'foreign_key_checks' variable`，
   看着像变量问题，实际是权限问题。
3. **`allowPublicKeyRetrieval=true` 必需。** MySQL 8 默认 `caching_sha2_password`，本地走非 SSL 连接时
   缺它会报 `Public Key Retrieval is not allowed`。

**验证记录**：应用启动 1.48s，`/actuator/health` 返回 `{"status":"UP"}`，Flyway 日志
`Database: jdbc:mysql://.../regent (MySQL 8.0)` → `Successfully validated 0 migrations`，并在 `regent` 库中
创建了 `flyway_schema_history` 表。迁移链路确认可用，只等 `V1__init_schema.sql` 落下去。

---

## 四、目录结构

模块划分与包结构以 `ARCHITECTURE.md` 第四节为唯一事实源，这里只列与选型直接相关的落点：

```
regent/
├── pom.xml                      # 聚合 POM，<parent> 指向 api-parent
├── regent-commons/              # 共享内核（DomainException / DecisionLevel）
├── regent-core/                 # 领域层 + 应用层，纯 Java，零框架依赖
├── regent-infrastructure/       # 持久化 / codex 适配 / Flyway 脚本 / 出参契约
│   └── src/main/resources/
│       ├── db/migration/        # ★ 唯一的迁移脚本位置（V1、V2…）
│       ├── db/seed/             # 可选演示数据（默认不加载，启用方式见文件头注释）
│       ├── mapper/              # MyBatis XML
│       └── codex/               # 出参 schema 与上报约定（见第五节）
├── regent-interfaces/           # REST、DTO、调度入口
├── regent-boot/                 # Spring Boot 启动与装配
├── regent-relay/                # 公网中继与本地连接器，零第三方运行时依赖
├── regent-web/                  # 前端（Vue 3 + Tailwind，见 FRONTEND.md）
└── deploy/                      # 本地启停脚本 + 远程发布脚本
```

DTO 收在 `regent-interfaces` 的 `interfaces/dto` 包下，**没有单独的 `regent-api` 模块** ——
对外契约目前只有本仓库的 REST 层与前端两个消费方，单独拆模块是设想里才成立的抽象。

---

## 五、Codex 出参契约

**现状是手写 schema，不是生成**。契约文件收在 `regent-infrastructure/src/main/resources/codex/`：

- `codex-exec-result.schema.json` —— 出参的 JSON Schema（draft-07，`additionalProperties: false`）
- `platform-contract.md` —— 给模型看的「平台上报约定」，讲清三种结局与定级尺度

两个文件随 classpath 打包；执行时由 `SchemaFileProvider` 首次调用落到 `~/.regent/`，因为
`codex exec --output-schema` **只接受文件路径**，不接受内联 schema。运行产物一律不落仓库。

出参有六个字段（`status` / `summary` / `changedFiles` / `branch` / `decision` / `bugs`）。
`bugs` 是执行器发现的缺陷清单，平台会把每项落成来源任务可追溯的 Bug 单；
没有发现缺陷时传空数组。验证结论仍写在 `summary` 里，暂未拆成独立字段：

```java
public record CodexExecResult(
    Status status,                 // DONE / BLOCKED / FAILED
    String summary,
    List<String> changedFiles,
    BranchDto branch,              // 可选；requested=true 时平台在成功后创建 feature 分支
    DecisionDto decision,          // 只有 BLOCKED 时是对象，其余为 null
    List<BugDto> bugs              // 本轮发现的缺陷，没有则为空
) {}

public record BranchDto(
    boolean requested,
    String name,
    String reason
) {}

public record DecisionDto(
    Level level,                   // L0 / L1 / L2 / L3
    String question,
    List<String> options,
    String recommendation,
    boolean reversible,
    String blastRadius
) {}

public record BugDto(
    String title,                  // 一句可执行的缺陷标题
    Severity severity,             // BLOCKER / MAJOR / MINOR / TRIVIAL
    String reproduction,           // 不知道就留空
    String filePath,               // 相对项目根，不知道就留空
    String detail
) {}
```

`branch` 只是执行器的隔离分支请求。项目根目录不是有效 Git 仓库（普通目录、空仓库或
多个 Git 仓库的父目录）时，平台忽略该请求并保持 `branch_name = NULL`，任务仍按执行结果
正常收敛；不会因为当前目录没有分支名判定失败。

**DTO 与 schema 的一致性还没有单测守**。`CodexReportTranslator` 按字段名读 `-o` 落盘的 JSON，
schema 改字段而解析没跟上（或反之）只有集成测试能发现。想补的那一步是 DTO 用
`victools/jsonschema-generator` 生成、加一条「生成结果必须等于提交的文件」的单测——
在那之前，改 schema 必须同步改 `CodexReportTranslator` 与 `CodexCliAdapterTest`：

```java
@Test
void schemaMustMatchCommittedFile() {
    var generated = Generator.generate(CodexExecResult.class);
    var committed = read("codex-exec-result.schema.json");
    assertEquals(committed, generated,
        "DTO 改了但 schema 文件没更新，或反之");
}
```

实际命令行（`CodexCliAdapter.commandLine`，新会话）：

```bash
codex exec \
  -C <项目目录> \
  -s workspace-write \
  --json \
  --output-schema <~/.regent 下落盘的 schema 文件> \
  -o <末条消息输出文件> \
  -                                  # 位置参数：指令从 stdin 读，绝不进 argv
```

几条踩过坑才定下的规矩：

1. **指令走 stdin，不进 argv**。JVM 的 `file.encoding` 常常不是 UTF-8（surefire、systemd、
   `LANG` 未设置时都是 `US-ASCII`），中文 argv 会在起进程时被逐字换成 `?` 且不报错。
   末尾的 `-` 就是「从 stdin 读指令」，字节由我们自己按 UTF-8 写；临时文件用完即删。
   同时 `deploy/` 的启动脚本与 systemd 单元钉死 `-Dfile.encoding=UTF-8`。
2. **`resume` 没有 `-C` 与 `-s`**：`codex exec resume <thread_id>` 的工作目录靠子进程 cwd
   （适配器已 `ProcessBuilder.directory(项目目录)`），沙箱模式用
   `-c sandbox_mode="workspace-write"` 覆盖。这一点是从 `--help` 逐条核对出来的。
3. **`--json` 与 `-o` 是两条路**：`--json` 是 stdout 的 JSONL 事件流（逐行解析、落 `run_event`，
   并从中取 `thread_id` 写回任务），`-o` 落盘的是**纯 JSON** 末条消息（不是 JSONL），
   交给 `CodexReportTranslator` 按 schema 翻译成 `ExecResult`。
4. **stderr 重定向到文件**，不读到管道里：只读 stdout 而不管 stderr，缓冲区写满时子进程会卡死。
5. **读不懂出参不抛异常**：翻译成一条失败的执行记录，让工作线程继续活着，任务不会停在 RUNNING。

决策批准后的续跑（人工结论作为新指令的一部分注入）：

```bash
codex exec resume <thread_id> \
  -c sandbox_mode="workspace-write" \
  --json \
  --output-schema <同上> \
  -o <新的末条消息输出文件> \
  -
```

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

> **前置条件已就绪**：本机 MySQL 8.0.43 常驻于 `127.0.0.1:3306`，`regent` / `regent_test` 两个 schema
> 与受限账号均已创建并通过启动验证（详见 3.7）。守护脚本仍需把 MySQL 的存活纳入探活范围——
> **DB 挂了整条自动化就停摆**，这是选 MySQL 换来的代价。

---

## 七、测试与质量

- **必须单测**：`DecisionArbiter`（分级仲裁，分支全覆盖）、Codex JSONL 解析与出参翻译
  （`CodexCliAdapterTest` 9 个用例）、Codex 进程的指令编码与事件派发线程
- **待补**：DTO↔Schema 一致性（契约还是手写文件，见第五节）、通知通道降级（通道未落地）
- **集成测试**：用假 codex 脚本回放固定 JSONL，跑完整任务生命周期，不真调模型；DB 指向 `regent_test`
- **Flyway 测试**：`@SpringBootTest` 启动时自动在 `regent_test` 上跑全量迁移，顺便验证脚本可执行
- **门槛**：`mvn -q clean verify` 全绿

---

## 八、配置

`application-{profile}.yml` 为主，密码走环境变量，不进 Git。**分级参数不在配置文件里**：
项目阈值来自 `project.default_decision_level` 列，规则来自 `decision_rule` 表 —— 它们是要按项目
调的业务数据，不是部署参数。配置文件只管「进程怎么跑」：

```yaml
regent:
  home: ${REGENT_HOME:${user.home}/.regent}   # schema 与执行临时文件；日志、pid 也写这里
  execution:
    codex-command: ${REGENT_CODEX_COMMAND:codex}
    timeout: ${REGENT_EXEC_TIMEOUT:PT30M}     # 单次执行的墙钟上限，ISO-8601
    max-concurrency: ${REGENT_MAX_CONCURRENCY:1}   # 仅首次默认；界面保存后以数据库配置为准
    branch-enabled: ${REGENT_BRANCH_ENABLED:true}  # 根目录有效 Git 仓库才记录分支；非 Git 目录分支为空
    dispatch:                                 # 派发心跳。关了就只能靠人点「派发执行」
      enabled: ${REGENT_DISPATCH_ENABLED:true}
      interval-ms: 15000
      batch-size: 3
  auth:
    session-ttl: PT168H              # 不保持登录时，服务端会话最多保留七天
    remembered-session-ttl: PT4320H  # 保持登录 180 天；剩余一半时自动顺延
    cookie-secure: false             # 生产 HTTPS 环境设为 true
    email-code:
      ttl: PT5M                    # 注册验证码有效期
      resend-cooldown: PT60S       # 同一邮箱重发冷却
      max-per-day: 10              # 同一邮箱每日发送上限
      home-url: ${REGENT_PUBLIC_URL:http://127.0.0.1:12580}

spring:
  mail:                            # 注册验证码 SMTP；密码只放本地配置或环境变量
    host: ${SPRING_MAIL_HOST:smtp.126.com}
    port: ${SPRING_MAIL_PORT:465}
    username: ${SPRING_MAIL_USERNAME:}
    password: ${SPRING_MAIL_PASSWORD:}
```

> **本地 profile 把 `regent.execution.dispatch.enabled` 覆写成 `false`**：一旦按需把演示数据
> 灌回来（项目路径就是真实仓库），心跳一开，起个服务就会真的跑起来。理由与处置见 README
> 与 `deploy/README.md`。演示数据本身默认不加载，见本节与种子脚本头部的说明。

---

## 九、风险与备选

| 风险 | 说明 | 应对 |
|---|---|---|
| MySQL 未常驻 | 本机没有 MySQL 服务，平台可用性从此绑定在 DB 上 | 先装常驻实例 + 纳入 start-all/status 脚本；冒烟时先探 DB |
| Flyway 误删库 | `clean` 会删整库 | `clean-disabled: true` 显式声明；专用 schema 限制爆炸半径 |
| 迁移脚本写错已执行 | 生产已跑过，改脚本会污染历史 | 铁律第 1 条：只追加不修改；发版前 review 脚本 |
| DTO 与 Schema 漂移 | 契约是手写文件，解析器按字段名读，两边天然会漂 | 人工纪律：改 schema 必须同步改 `CodexReportTranslator` 与适配器测试；计划用 victools 生成 + 一致性单测兜底（见第五节） |
| `--output-schema` 被违反 | 模型未必每次严格合规 | 校验失败按 `FAILED` 处理并保留原始输出，绝不猜 |
| 无人值守被审批卡死 | headless 下不能出现交互式确认 | 每项目配 Codex `.rules` 白名单；危险命令一律判 `L3` |
| 测试基建 | Testcontainers 需 Docker，本机没有 | 走独立 `regent_test` schema 直连 |

---

## 十、待定项

1. ~~前端选型~~ **已定**：Vue 3 + Vite + Tailwind CSS + reka-ui，对标 macOS 原生观感，详见 `FRONTEND.md`。
   工作区现有前端（`app-audit-frontend`、`r_data_service/web`）**全是 React**，上 Vue 意味着将来维护两套前端规范——该代价已确认接受。
2. ~~MySQL 实例位置~~ **已定**：指向本机常驻实例（MySQL 8.0.43），建 `regent` / `regent_test` 两个独立 schema，
   不复用其它项目库。详见 3.7。
3. ~~工作区根 `.git` 干扰~~ **已定**：`regent/` 内已独立 `git init`（当前分支 `main`），
   父目录的空 `.git` 不再影响跟踪范围。

---

## 十一、下一步

进度与下一步**以 `ARCHITECTURE.md` 第十一节为唯一事实源**，这里不再维护第二份会漂移的清单。
当前剩下的三件事：

1. 邮件与推送通道，以及邮件里的「一键批准」入口（`POST /api/approvals/{token}`）
2. 需求录入与拆解、Bug 单的写操作（派发修复任务）
3. 决策规则的维护界面
