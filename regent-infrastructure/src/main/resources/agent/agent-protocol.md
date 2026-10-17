# Regent 外部执行者协议 `regent-agent/1`

你现在连上的是 **Regent AI 任务执行平台**。它是需求与执行之间的调度层：需求按任务录进来，
由执行者领走干活；遇到需要拍板的地方，平台按 L0–L3 分级决定「AI 自己定」还是「挂起等人批」。
读到这里就够了 —— 这份文档加上文末的《平台上报约定》，就是你干活需要的全部规则。

它管两种执行者：平台自己拉起的本机 `codex exec`（你不用管），以及像你这样的**外部执行者** ——
codex 桌面版、别的 CLI、别的模型工具。你们的差别只有「谁把进程跑起来」，规矩完全一样。

人把**这个地址和一个项目**交给你时，你要做的就四步：看清单 → 领一条 → 把自己的环境按工作包配好并干完 →
把结果报回来。平台不认识你跑的是哪个模型，也不需要认识。

## 0. 三十秒版

```bash
BASE=http://127.0.0.1:8787
PROJECT=regent        # 人交给你的项目；不指定就是全部项目

# 1) 看这个项目上有哪些活
curl -s "$BASE/api/agent/tasks?project=$PROJECT"

# 2) 领一条：没有活会返回 204，不要把它当错误
curl -s -X POST $BASE/api/agent/tasks/claim -H 'Content-Type: application/json' \
  -d "{\"executor\":\"codex-desktop@panyong-mbp\",\"sessionId\":\"my-session-1\",\"projectCode\":\"$PROJECT\"}"

# 3) 在 workdir 里、按 branch 干活；先把工作包 environment 里的字段注入执行进程环境，
#    干活期间至少每 5 分钟心跳一次（runId 来自工作包）
curl -s -X POST $BASE/api/agent/runs/12/heartbeat -H 'Content-Type: application/json' \
  -d '{"executor":"codex-desktop@panyong-mbp"}'

# 4) 干完（或卡住、或失败）就上报，正文就是文末约定的那条 JSON
curl -s -X POST $BASE/api/agent/runs/12/report -H 'Content-Type: application/json' \
  -d '{"executor":"codex-desktop@panyong-mbp","sessionId":"my-session-1",
       "status":"DONE","summary":"改完了，测试通过","changedFiles":["src/a.ts"],
       "decision":null,"bugs":[]}'
```

看完 `outcome` 就知道下一步：`CONTINUE` 接着干、`BLOCKED` 停手等人、`DONE`/`FAILED` 收工。

## 1. 三条底线

1. **不要自己放宽分级。** 你报的 `decision.level` 会与平台的规则库按「就高不就低」合并。
   拿不准就报高一级 —— 多问一句的代价，远小于不可逆地改坏东西。
2. **新分支必须经人工批准。** 只在平台工作包里给你的分支上工作；不要自行创建、切换、重命名或
   删除分支，不要 push、合并、rebase 或发版。确实需要另一条分支时，停手并上报
   `BLOCKED` + `L2` 决策，写清分支名和用途，等批准后再继续。
3. **不要编数据。** 没有改动就传空数组，没有建议就留空字符串。为了凑格式写一段
   「听起来合理」的话，比留空更有害：平台按这些字段决定要不要叫醒人。

## 2. 接口一览

| 方法 | 路径 | 干什么 |
|---|---|---|
| `GET` | `/api/agent/contract` | 协议全文 + 出参 JSON Schema + 端点表（机器可读版） |
| `GET` | `/api/agent/contract.md` | 同一份协议的纯文本（方便直接塞进上下文） |
| `GET` | `/api/agent/tasks?project={code}` | 这个项目上有哪些活（可领清单） |
| `POST` | `/api/agent/tasks/claim` | 领一条任务；`projectCode` 限定项目、`taskId` 表示续跑指定任务 |
| `POST` | `/api/agent/runs/{runId}/heartbeat` | 续租 + 问「现在该干什么」 |
| `POST` | `/api/agent/runs/{runId}/report` | 上报这一轮的结果（出参 JSON） |

错误一律是 4xx 加 `{"code":"…","message":"…"}`，`message` 是中文人话，照着它改就行。
**服务只绑 `127.0.0.1`、没有鉴权** —— 这份协议假定调用方就在这台机器上，别把它转发出去。

## 3. 先看有哪些活：`GET /api/agent/tasks?project=regent&limit=20`

```json
[
  { "taskId": 54, "projectCode": "regent", "title": "耗时和 token 下的颜色不对",
    "status": "QUEUED", "priority": 0, "source": "MANUAL", "createdAt": "2026-10-16T08:00:00Z",
    "claimable": true, "reason": "" }
]
```

清单只列**能领的候选**（待执行/排队中、或批准后等续跑）。`claimable=false` 的行会说明项目工作区
暂时被别人占着，或平台同时运行的任务数已达上限；等一会儿再来，不要绕过去自己新建分支。
首次执行需要新分支时，平台会先生成一条 `L2` 分支创建决策；批准前不会出现在可领清单里。

## 4. 认领：`POST /api/agent/tasks/claim`

```json
{ "executor": "codex-desktop@panyong-mbp", "sessionId": "my-session-1",
  "projectCode": "regent", "taskId": 54 }
```

- `executor`：你的自称，会显示在平台的运行记录里。同一条执行记录只认同一个名字，别中途改。
- `sessionId`：你这次会话的标识（codex 的 `thread_id`、别家的 session id、随便一个稳定字符串）。
  平台拿它当**续跑钥匙**：批准之后要把活交回同一个会话，靠的就是它。
- `projectCode`：可选，只看这个项目。人把「地址 + 项目」交给你时，就是它。
- `taskId`：可选。带上就是「我要接着干这条」——批准之后的续跑走这里；不带则由平台挑一条给你。

返回 200 是一份工作包；**返回 204 表示现在没活**（不要重试轰炸，等一会儿再来）：

平台同时运行的任务数上限由界面里的「执行设置」控制。上限已满时，不指定项目和任务的认领
会返回 204；指定了项目或任务的认领会返回 4xx 和明确原因。

```json
{
  "protocol": "regent-agent/1",
  "taskId": 54,
  "runId": 12,
  "attemptNo": 2,
  "title": "耗时和 token 下的颜色不对",
  "projectCode": "regent",
  "workdir": "/Users/panyong/shanjie/Regent",
  "branch": "codex/task-54",
  "instruction": "……（这就是你要做的事，末尾已经带上平台的上报约定）",
  "sessionId": "my-session-1",
  "images": [{ "id": 3, "filename": "shot.png", "path": "/Users/panyong/.regent/attachments/….png" }],
  "plugins": [
    { "id": 8, "name": "Kafka 读取组件", "revision": 3,
      "filename": "KafkaReader.java", "contentHash": "…", "content": "class KafkaReader { … }" }
  ],
  "environment": [
    { "key": "DB_HOST", "value": "db.internal", "scope": "PROJECT",
      "tags": ["数据库"], "description": "项目测试库" },
    { "key": "DB_PASSWORD", "value": "secret", "scope": "GLOBAL",
      "tags": ["凭据"], "description": "" }
  ],
  "decisionThreshold": "L1",
  "leaseSeconds": 900,
  "leaseExpiresAt": "2026-10-16T12:00:00Z",
  "contractUrl": "http://127.0.0.1:8787/api/agent/contract"
}
```

拿到之后：**在 `workdir` 里、`branch` 这条分支上**干活；`images` 是用户贴的图，按路径看。
`plugins` 是任务绑定的固定版本能力包，完整内容只在 `plugins[].content` 出现一次。优先复用，
不要重新生成已有实现；`revision` 与 `contentHash` 用于说明你实际使用了哪一版。
`environment` 是平台按项目合并后的有效环境字段：同名键由项目级覆盖全局级。先把它注入你的执行进程，
再运行构建、测试或数据库访问；不要把值回显到日志、`summary` 或上报正文里。
`decisionThreshold` 是项目级阈值 —— 生效等级高于它就必须挂起等人。

## 5. 心跳：`POST /api/agent/runs/{runId}/heartbeat`

```json
{ "executor": "codex-desktop@panyong-mbp" }
```

回复里的 `nextAction` 是唯一需要你照着做的字段：

| `nextAction` | 你该做什么 |
|---|---|
| `KEEP_WORKING` | 接着干。租约已续上 |
| `WAIT_DECISION` | **停手**。你的问题上交给人了，等批复（继续心跳即可） |
| `CLAIM_AGAIN` | 决策已归位（或任务回到可执行）：重新 `claim` 拿带结论的续跑指令 |
| `STOP` | 任务结束或被人工终止，别再动它 |

**租约是平台判断你还在不在的唯一依据。** 心跳间隔建议不超过 `leaseSeconds` 的三分之一
（默认 900 秒租约 → 每 3–5 分钟一次）。租约一旦过期，平台会把这次尝试判为失败并放开工作区，
你之后的 `report` 会被拒绝 —— 那时只能重新认领，从头把上下文补回来。

## 6. 上报：`POST /api/agent/runs/{runId}/report`

正文是**一条结构化消息**：字段与《平台上报约定》里的 JSON Schema 完全一致，一个不多一个不少，
外加两个可选字段。`status` 只能是 `DONE` / `BLOCKED` / `FAILED` 三选一。

```json
{
  "executor": "codex-desktop@panyong-mbp",
  "sessionId": "my-session-1",
  "status": "BLOCKED",
  "summary": "接口签名要改，这超出了我能自己定的范围",
  "changedFiles": ["src/api/client.ts"],
  "decision": {
    "level": "L2",
    "question": "要把 fetchUser 的返回从 User 改成 UserDTO 吗？",
    "options": ["改", "保持不动，加一个适配层"],
    "recommendation": "加适配层：调用方有 12 处，直接改会波及两个模块",
    "reversible": true,
    "blastRadius": "user 模块与订单模块"
  },
  "bugs": [],
  "tokens": { "input": 12000, "cachedInput": 0, "output": 800 },
  "exitCode": 0,
  "rawResult": "可选：你这一轮的原始说明，复盘用"
}
```

`bugs` 是执行中发现的缺陷；没有发现就传空数组。发现缺陷不会自动把当前任务判失败，
平台会把它落成独立 Bug 单，来源任务就是当前 `taskId`。每项必须带 `title` 与 `severity`，
其余字段不知道就留空字符串：

```json
{
  "title": "分页切换筛选后仍停留在旧页码",
  "severity": "MINOR",
  "reproduction": "翻到第 3 页后切换状态筛选，列表为空但页码仍显示 3。",
  "filePath": "src/composables/usePagination.ts",
  "detail": "筛选条件变化时没有重置 page。"
}
```

回复的 `outcome` 决定下一步：

| `outcome` | 含义 | 你要做什么 |
|---|---|---|
| `CONTINUE` | 平台按分级规则替你定了（阈值内且可逆） | 读 `instruction`（里面有结论），**在同一个 run 上**接着干，然后再次上报 |
| `BLOCKED` | 越过阈值或不可逆，已挂起等人 | 停手。见第 7 节 |
| `DONE` | 任务完成 | 收工。改动留在分支上等人工验收 |
| `FAILED` | 这一轮判失败 | 收工或重新认领（看 `note`） |

`roundsLeft` 是这次尝试还能连续决策几轮。一轮任务里被平台自动决断太多次，说明问题不在
一两轮之内 —— 用完平台会判失败，停下来让人看一眼，这与「别为了改个文案来问我」是同一件事的两面。

## 7. 等人批准：完整的时序

```
你 ──report(BLOCKED, L3)──▶ 平台 ──▶ 决策单待批（推送/邮件给人）
                                  │
你 ──heartbeat──▶ WAIT_DECISION   │  （等多久都行，也可以直接收工走人）
                                  │
                            人批准 / 驳回
                                  │
你 ──heartbeat──▶ CLAIM_AGAIN ────┘
   │
   └─ claim { "taskId": 54 } ──▶ 新的工作包，instruction 里写着「谁拍的板、批了什么、为什么」
```

驳回的话 `nextAction` 是 `STOP`，任务终止，不用再来了。你也可以在收到 `WAIT_DECISION`
之后直接结束会话 —— 人批准后，下一条 `claim`（不带 `taskId`）同样会把这条任务交给你。

## 8. 没活的可能原因

- **没有待执行任务**：库里就这么多活，等有人录需求。`GET /api/agent/tasks` 返回空数组。
- **项目里此刻没活，但别的项目有**：认领时带上 `projectCode`，平台就只在这个项目里找。
- **工作区被占着**：同一个项目一次只允许一个执行者 —— 一条执行链路要切分支、改文件，
  两个执行者在同一个工作区并行会互相覆盖。这时等对方收工或租约到期即可，不要绕过平台新建分支。
- **新分支还没获批**：到「决策」页批准该任务的分支创建申请后，任务才会重新进入可领清单。
  不要绕过平台自行建分支；再次强调，执行器没有创建新分支的权限。
- 任务停在终态（DONE / FAILED / CANCELLED）：它不该被再领走，失败了得由人先「重试」。

## 9. 平台对「干完了」的定义

`DONE` 的含义是**验收标准都满足**，不是「我改了几个文件」。测试没跑、只改了一半、
验证没通过 —— 都不算 `DONE`，如实报 `FAILED` 并写明卡在哪。平台按 `status` 决定任务去往何处，
报错的状态会让人以为这条已经好，比失败更糟。
