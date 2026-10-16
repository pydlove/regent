# 部署与启动脚本

两套脚本，互不依赖：

```
deploy/local/     本机开发：起后端 + 前端，给你点开验证
deploy/remote/    发布到 Linux 服务器：构建 + 上传 + systemd 托管
```

## 本地：`deploy/local/`

| 脚本 | 作用 |
|---|---|
| `start-all.sh` | 一键起后端 + 前端（等价于依次跑下面两个） |
| `start-backend.sh` | 只起后端。**已在监听就不重复起**，直接返回 |
| `start-frontend.sh` | 只起前端（vite dev）。缺 `node_modules` 时先 `npm install` |
| `stop-all.sh` | 按 pid 停，pid 文件丢了就按端口兜底 |
| `restart.sh` | `stop-all` → 等 2 秒 → `start-all` |
| `status.sh` | 端口探活 + 健康检查，打印两行状态 |

```bash
./deploy/local/start-all.sh     # 起
./deploy/local/status.sh        # 看
./deploy/local/restart.sh       # 重启（改完后端要重启，前端 vite 自己热更新）
./deploy/local/stop-all.sh      # 停
```

启动后：

- 前端 <http://127.0.0.1:12580/>
- 后端 <http://127.0.0.1:8787/actuator/health>
- 日志 `~/.regent/logs/backend.log`、`~/.regent/logs/frontend.log`
- pid `~/.regent/*.pid`

**运行时产物一律写 `~/.regent/`，不写仓库。** 仓库里出现 pid 或日志文件即为配置错误
（根 `.gitignore` 也会挡住）。

几个刻意的取舍：

- **探活优先于 pid 文件。** 机器重启后 pid 文件会残留，而 pid 会被复用 ——
  只有端口在听才算真的在跑。`status.sh` 因此先 `lsof` 再回落到 pid 文件。
- **前端直接起 `node_modules/.bin/vite`，不走 `npm run dev`。** npm 会多套一层壳进程，
  pid 文件记的是壳的号；停服务时杀掉壳、真正监听端口的 node 还活着，只能靠端口兜底补救。
- **后端 jar 不存在时自动 `mvn package`。** 首次克隆后直接 `start-all.sh` 也能跑起来。
- 数据库凭据读 `regent-boot/src/main/resources/application-local.yml`（已 gitignore），
  也可用 `REGENT_DB_USERNAME` / `REGENT_DB_PASSWORD` 覆盖。
- **本地派发心跳默认关闭**（`application-local.yml` 里 `regent.execution.dispatch.enabled: false`）。
  一旦按需灌了演示数据（项目路径就是真实仓库），心跳一开，起个服务就会真的把演示任务跑起来。
  想看自动派发再改 `true`；只验证单条任务，用界面的「派发执行」或
  `POST /api/tasks/{id}/dispatch`。远端 profile 由 `REGENT_DISPATCH_ENABLED` 控制，默认开。

依赖：JDK 17、Maven 3.9+、Node 20+、本机 MySQL（`regent` / `regent_test` 两个 schema）。

## 发布：`deploy/remote/`

服务端布局（与 systemd 单元、Nginx 约定一致）：

```
/root/app/regent/backend/    regent-boot.jar + application-prod.yml（仅服务器上存在）
/root/app/regent/frontend/   dist/
/root/app/regent/script/     start.sh / stop.sh / restart.sh
/root/app/regent/logs/
```

```bash
export REGENT_SERVER_IP=1.2.3.4          # 不配就直接报错退出，不会误传到 127.0.0.1
export REGENT_SERVER_USER=root
export REGENT_SSH_KEY=~/.ssh/id_rsa      # 或 REGENT_SERVER_PASSWORD=... （需要 sshpass）
./deploy/remote/deploy.sh
```

`deploy.sh` 依次做六件事：构建后端 → 构建前端 → 建目录 → 停服务并上传 jar →
上传前端 `dist/` 与服务端启停脚本 → 装 systemd 单元并启动。

服务器上还需要你手工做两件事（脚本会提示）：

1. Nginx 的 `root` 指到 `/root/app/regent/frontend/dist`，然后 `nginx -s reload`；
2. 放一份 `/root/app/regent/backend/application-prod.yml`，**凭据永远不进仓库**。
   更推荐用 `backend/.env`（systemd 的 `EnvironmentFile`），写 `REGENT_DB_USERNAME` /
   `REGENT_DB_PASSWORD` 两行即可。

前端 `dist/` 是静态产物，没有 Node 运行时依赖；后端由 systemd 托管，开机自启、崩溃重试。
`SystemMaxUse` 之类的日志轮转不在脚本里，服务器上按需配 `journald` 即可。
