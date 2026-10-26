# Git 提交记录管理设计

| 项目 | 内容 |
|---|---|
| 需求编号 | REG-V106-GIT |
| 文档状态 | MVP 已实现（R100B038） |
| 编写日期 | 2026-10-21 |
| 所属版本 | Regent_V1.0.6 |

## 一、设计结论

新增独立支撑上下文 `gitcommit`，不要让 `review` 聚合承担提交历史。

核心结论：

1. `GitCommitRecord` 是聚合根，只追加，不在历史记录上反复修改提交事实。
2. 每次提交尝试一条记录，失败也记录。
3. 同一审阅快照最多一条成功记录。
4. Git 仍是仓库事实源，平台只保存审计投影和可追溯引用。
5. 目标分包：

```text
com.pydlove.regent.gitcommit
  ├─ application
  │    ├─ command
  │    └─ query
  ├─ domain
  │    ├─ model
  │    ├─ port
  │    └─ repository
  └─ infrastructure
       ├─ git
       └─ persistence
```

6. 现有 `/api/tasks/{taskId}/review/git-commit` 保持兼容，控制器委托给 `gitcommit` 应用服务。
7. `review_snapshot` 上的 `commit_sha/message/committed_*` 暂时保留为当前提交投影；
   `git_commit_record` 是完整审计事实源。

## 二、为什么不继续放在 review

`ReviewSnapshot` 的职责是固定一次审阅的代码事实和人工验收状态。

提交记录具有不同生命周期：

- 一个快照允许多次失败尝试。
- 后续可能加入 Push、远端状态和 CI 状态。
- 需要独立分页、筛选和按项目审计。
- 提交失败不会改变快照状态。

如果继续把字段挂在快照上，会出现：

- 历史尝试无法保存。
- 查询列表需要扫描审阅聚合。
- 每次扩展 Pull/Push 都改快照表和审查聚合。

因此使用独立上下文，但仍由审阅应用服务编排提交前置条件。

## 三、领域模型

### 3.1 GitCommitRecord

聚合根字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `GitCommitRecordId` | 强类型 ID |
| `projectId` | `ProjectId` | 所属项目 |
| `taskId` | `TaskId` | 来源任务 |
| `taskRunId` | `TaskRunId` | 来源执行记录 |
| `snapshotId` | `ReviewSnapshotId` | 来源审阅快照 |
| `reviewDecisionId` | `ReviewDecisionId` | 整单审阅结论 |
| `operatorId` | `UserId` | 发起人 |
| `ticket` | `String` | 6 位 Bug 编号 |
| `message` | `String` | 最终提交说明 |
| `branchName` | `String` | 提交所在分支 |
| `baseRef` | `String` | 审阅基线 |
| `commitSha` | `String` | 成功后的 SHA |
| `filePaths` | `List<String>` | 本次提交文件 |
| `status` | `GitCommitStatus` | RUNNING/SUCCEEDED/FAILED |
| `failureReason` | `String` | 失败原因 |
| `outputSummary` | `String` | 脱敏输出摘要 |
| `startedAt` | `Instant` | 开始时间 |
| `finishedAt` | `Instant` | 结束时间 |

### 3.2 状态机

```text
RUNNING ──succeed──▶ SUCCEEDED
RUNNING ──fail──▶ FAILED
```

守卫：

- 只有 `RUNNING` 可以收敛。
- `SUCCEEDED` 必须带合法 Commit SHA。
- `FAILED` 必须带失败原因。
- `SUCCEEDED` 后不能再修改 SHA 或说明。
- `FAILED` 不能直接改状态；重试会创建新记录。

### 3.3 状态枚举

```java
public enum GitCommitStatus {
    RUNNING,
    SUCCEEDED,
    FAILED
}
```

## 四、数据库设计

下一版本追加 Flyway：

```text
V47__git_commit_record.sql
```

### 4.1 git_commit_record

```sql
CREATE TABLE git_commit_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    task_run_id BIGINT NOT NULL,
    review_snapshot_id BIGINT NOT NULL,
    review_decision_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    ticket VARCHAR(6) NOT NULL,
    commit_message TEXT NOT NULL,
    branch_name VARCHAR(128) NOT NULL,
    base_ref VARCHAR(128) DEFAULT NULL,
    commit_sha VARCHAR(64) DEFAULT NULL,
    file_paths_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    failure_reason VARCHAR(1024) DEFAULT NULL,
    output_summary TEXT DEFAULT NULL,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) DEFAULT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_git_commit_snapshot_sha (review_snapshot_id, commit_sha),
    KEY idx_git_commit_project_time (project_id, started_at, id),
    KEY idx_git_commit_task_time (task_id, started_at, id),
    KEY idx_git_commit_sha (commit_sha),
    KEY idx_git_commit_operator (operator_id, started_at)
);
```

### 4.2 为什么成功唯一键不是只加 snapshot_id

失败记录需要允许同一快照多次尝试，因此不能直接 `UNIQUE(review_snapshot_id)`。

MySQL 唯一索引允许 `NULL` 重复，因此：

- 多次失败记录的 `commit_sha = NULL` 可以共存。
- 成功记录的 `commit_sha` 非空，同一快照第二条成功记录会被数据库拒绝。

应用层仍需在提交前查询已有成功记录，数据库唯一键是最终防线。

### 4.3 历史数据回填

迁移中从 `review_snapshot` 回填已提交快照：

- `review_snapshot.status = 'COMMITTED'`
- `commit_sha IS NOT NULL`

`review_decision_id` 取该快照最新 `APPROVED` 结论。

如果极少数历史数据找不到通过结论，迁移保留记录但不自动补齐，并在设计说明中列为脏数据审计项。

### 4.4 review_snapshot 字段处理

MVP 保留现有字段：

- `commit_sha`
- `commit_message`
- `committed_by`
- `committed_at`

它们作为“当前成功提交投影”，避免破坏现有审阅和部署门禁。

后续版本可改为：

- `latest_commit_record_id`

通过该字段指向 `git_commit_record`，不在快照上重复保存提交详情。

## 五、领域端口

### 5.1 GitCommitExecutor

现有 `GitCommitGateway` 可以迁移到 `gitcommit.domain.port`，并扩展为：

```java
public interface GitCommitExecutor {
    Inspection inspect(String projectDirectory, List<String> reviewedPaths);
    CommitResult commit(String projectDirectory, List<String> reviewedPaths, String message);
    boolean matchesCommit(String projectDirectory, String commitSha, List<String> reviewedPaths);
}
```

`Inspection` 至少包含：

- 是否 Git 仓库
- 当前分支
- 当前 HEAD
- 暂存区是否干净
- 已暂存文件

### 5.2 GitCommitRecordRepository

```java
public interface GitCommitRecordRepository {
    GitCommitRecord save(GitCommitRecord record);
    Optional<GitCommitRecord> findById(GitCommitRecordId id);
    Optional<GitCommitRecord> findSucceededBySnapshot(ReviewSnapshotId snapshotId);
    List<GitCommitRecord> findByTask(TaskId taskId);
}
```

列表查询不复用聚合仓储，新增只读 `GitCommitQueryRepository`，直接返回查询投影。

## 六、应用服务

### 6.1 GitCommitApplicationService

职责：

1. 校验任务归属。
2. 校验审阅快照当前有效。
3. 校验最新人工结论为通过。
4. 校验逐 diff 必看项全部通过。
5. 校验 Bug 编号与提交说明。
6. 创建 `RUNNING` 记录。
7. 调用 Git 执行器。
8. 成功或失败收敛记录。
9. 成功后同步快照的当前提交投影。

不要在数据库事务中执行 Git 命令。

推荐流程：

```text
应用事务 A
  ├─ 校验审阅和重复提交
  └─ 保存 RUNNING 记录

事务外
  └─ 执行 Git commit

应用事务 B
  ├─ 成功：succeed(record) + snapshot.markCommitted(...)
  └─ 失败：fail(record, reason)
```

如果 Git 成功但事务 B 失败：

- 记录仍为 `RUNNING`，页面显示“结果待确认”。
- 提供“核对提交结果”动作，通过 Commit SHA 清单或 `HEAD` 修复记录。
- 不允许自动再次 commit。

### 6.2 并发控制

提交前：

- 查询 `findSucceededBySnapshot`。
- 检查是否存在同快照的 `RUNNING` 记录。
- 使用数据库唯一键兜底。

同一快照已有 `RUNNING` 时，第二次请求直接返回“提交正在进行”，不启动新进程。

## 七、查询模型

### 7.1 提交列表

```java
record GitCommitListItem(
    long id,
    long taskId,
    String taskTitle,
    long projectId,
    String projectName,
    String ticket,
    String message,
    String branchName,
    String shortSha,
    GitCommitStatus status,
    String operatorName,
    Instant startedAt,
    Instant finishedAt
) {}
```

### 7.2 提交详情

```java
record GitCommitDetail(
    GitCommitListItem summary,
    String fullSha,
    String baseRef,
    String repositoryPath,
    List<String> filePaths,
    String failureReason,
    String outputSummary,
    Long reviewSnapshotId,
    Long reviewDecisionId
) {}
```

## 八、API 设计

### 8.1 任务提交记录

```http
GET /api/tasks/{taskId}/git-commits
```

返回当前任务全部尝试，按时间倒序。

### 8.2 项目提交列表

```http
GET /api/git-commits
    ?projectId=
    &taskId=
    &status=
    &keyword=
    &from=
    &to=
    &page=1
    &size=20
```

### 8.3 提交详情

```http
GET /api/git-commits/{id}
```

### 8.4 发起提交

保留兼容接口：

```http
POST /api/tasks/{taskId}/review/git-commit
```

内部改为调用 `GitCommitApplicationService`。

### 8.5 核对结果

P1：

```http
POST /api/git-commits/{id}/reconcile
```

仅允许 `RUNNING` 且超时的记录；核对 `HEAD` 或提交信息后收敛状态。

不提供：

- `PUT`
- `PATCH`
- `DELETE`

## 九、前端设计

### 9.1 任务详情

审阅面板底部显示：

```text
最新提交
feat(102008): 优化告警记录处理逻辑
main · a1b2c3d4
查看全部 2 次提交
```

失败时：

```text
最近提交失败
暂存区存在未审阅文件
查看详情 / 重新提交
```

### 9.2 交付页

新增第三个标签：

```text
交付
  ├─ 部署
  ├─ 测试
  └─ Git 提交
```

列表默认列：

- 状态
- 任务
- 项目
- Bug 编号
- 提交说明
- 分支
- Commit
- 操作人
- 时间

### 9.3 详情抽屉

展示：

- 状态
- 完整提交说明
- Bug 编号
- 完整 SHA
- 分支
- 来源任务
- 审阅快照
- 文件清单
- 失败信息

操作：

- 复制 SHA
- 复制提交说明
- 打开任务
- 打开审阅工作区
- 失败重试（P1）

## 十、账户隔离

所有查询必须从登录会话取得当前账号：

- 列表 JOIN `project.owner_id`
- 详情先校验项目归属
- 任务提交列表先校验任务归属

错误统一返回“记录不存在”，不暴露其他账号数据是否存在。

## 十一、敏感信息

Git 输出进入数据库前：

- 移除或脱敏私钥。
- 脱敏 `password/passwd/token/secret/api_key/private_key`。
- 截断为 4000 字符。
- 不记录完整环境变量。
- 不记录远端凭据和 SSH 命令参数中的秘密。

## 十二、测试设计

### 12.1 领域测试

- RUNNING 成功收敛。
- RUNNING 失败收敛。
- SUCCEEDED 不能重复收敛。
- FAILED 不能改成 SUCCEEDED。
- 成功必须带合法 SHA。
- 失败必须带原因。

### 12.2 应用服务测试

- 未通过审阅不能提交。
- 过期快照不能提交。
- 重复成功提交被阻断。
- 同快照 RUNNING 时不能再次提交。
- 失败后可以创建新尝试。
- 成功后同步快照状态。
- 账号隔离。

### 12.3 Git 适配器测试

- 只提交审阅文件。
- 暂存区不干净时失败。
- detached HEAD 时失败。
- 失败输出脱敏。
- 成功 SHA 正确。
- Push 暂不测试，因为不在 MVP。

### 12.4 持久化集成测试

- 成功/失败记录完整读写。
- 回填历史 COMMITTED 记录。
- 唯一索引阻止同快照两个成功记录。
- 项目、任务和查询筛选正确。

### 12.5 前端测试

- 状态颜色和文案。
- 提交列表筛选。
- 详情字段。
- 失败重试。
- 空状态和加载状态。

## 十三、实施顺序

1. 新建 `GitCommitRecord` 领域模型、状态机和测试。
2. 新增 Flyway 表与历史回填。
3. 实现仓储和列表查询。
4. 把现有 `ReviewGitCommitService` 拆到 `gitcommit` 应用服务。
5. 任务详情接入提交历史。
6. 交付页新增 Git 提交标签。
7. 增加失败重试和结果核对。
8. 全量构建、MySQL 集成和 UI 截图验证。

## 十四、风险与取舍

| 风险 | 取舍 |
|---|---|
| Git 成功但数据库收敛失败 | 保留 RUNNING 并提供结果核对，不自动重复提交 |
| 失败尝试过多 | 列表按时间倒序，任务详情默认显示一次成功和最近一次失败 |
| 与 review_snapshot 字段重复 | MVP 保留投影以兼容现有部署门禁，后续收敛为 latest_commit_record_id |
| Push 需求蔓延 | MVP 只做本地提交，Push 单独设计 |
| 完整 Git 历史同步成本高 | 不镜像 Git 历史，只记录平台发起的提交动作 |

## 十五、设计验收结论

满足以下条件才进入开发：

1. 产品确认只管理平台发起提交，不镜像全部 Git 历史。
2. 产品确认失败尝试需要保留。
3. 产品确认 MVP 不包含 Push。
4. 技术确认新上下文 `gitcommit`，并保留现有 API 兼容。
5. 技术确认真实 MySQL 唯一索引与回填方案。
6. 安全确认输出摘要脱敏策略。
