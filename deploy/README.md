# 部署与启动脚本

两套脚本，按部署角色分工：

```
deploy/local/     本机：起 Regent、前端与公网中继连接器
deploy/remote/    公网服务器：构建并部署无状态中继与静态前端
```

## 本地：`deploy/local/`

| 脚本 | 作用 |
|---|---|
| `start-all.sh` | 一键起后端 + 前端 + 设备代理 + 已配置的中继连接器 |
| `start-backend.sh` | 只起后端。**已在监听就不重复起**，直接返回 |
| `start-frontend.sh` | 只起前端（vite dev）。缺 `node_modules` 时先 `npm install` |
| `start-relay.sh` | 起公网中继连接器；未配置 `~/.regent/relay-connector.env` 时安全跳过 |
| `connect-relay.sh` | 配对中继：读取服务器共享密钥 → 写 `~/.regent/relay-connector.env` → 重启连接器 → 探活 |
| `backend-agent.sh` | 把后端交给 launchd 托管（`install` / `uninstall` / `restart` / `status`），开机自启、崩溃重启 |
| `relay-agent.sh` | 把连接器交给 launchd 托管（`install` / `uninstall` / `restart` / `status`），开机自启、崩溃自动重启 |
| `device-agent.sh` | 把设备代理交给 launchd 托管，开机自启、崩溃自动重启 |
| `stop-all.sh` | 按 pid 停，pid 文件丢了就按端口兜底 |
| `restart.sh` | 构建后端 → `stop-all` → 等 2 秒 → `start-all` |
| `status.sh` | 端口探活 + 健康检查，打印两行状态 |

```bash
./deploy/local/start-all.sh     # 起
./deploy/local/status.sh        # 看
./deploy/local/restart.sh       # 重新构建并重启（前端 vite 自己热更新）
./deploy/local/backend-agent.sh install   # 可选：后端交给 launchd 常驻
./deploy/local/device-agent.sh install    # 设备代理常驻，浏览器可随时检测本机 Codex
./deploy/local/stop-all.sh      # 停
```

启动后：

- 前端 <http://127.0.0.1:12580/>
- 后端 <http://127.0.0.1:8787/actuator/health>
- 日志 `~/.regent/logs/backend.log`、`~/.regent/logs/frontend.log`
- pid `~/.regent/*.pid`

公网访问另外需要：

```bash
./deploy/local/connect-relay.sh          # 交互询问服务器 IP / SSH 端口 / 用户名；密钥读自服务器
./deploy/local/relay-agent.sh install    # 可选：交给 launchd 常驻，手机才随时打得开
```

连接器只主动访问服务器，不监听任何本地端口。未配置时 `start-all.sh` 会打印警告并跳过，
不影响本机直连 `http://127.0.0.1:12580/`。

`connect-relay.sh` 优先用 SSH 密钥登录，密钥不通且是交互终端时才问密码；
`deploy/remote/deploy.sh` 会直接传入密钥，所以服务器侧部署完就已经配对好，不需要再手工拷贝密码。

**连接器为什么要托管**：服务器只做转发，真正干活的 Regent 在本机，连接器一断，
手机打开公网地址就只会看到「本地 Regent 未连接」。`nohup` 起的连接器关掉终端就没了，
`relay-agent.sh install` 之后由 launchd 拉起：开机自启、崩溃重启、退出终端不影响。
托管期间 `start-relay.sh`、`stop-all.sh` 会主动避让，避免同一条配置被连两份。

**运行时产物一律写 `~/.regent/`，不写仓库。** 仓库里出现 pid 或日志文件即为配置错误
（根 `.gitignore` 也会挡住）。

几个刻意的取舍：

- **探活优先于 pid 文件。** 机器重启后 pid 文件会残留，而 pid 会被复用 ——
  只有端口在听才算真的在跑。`status.sh` 因此先 `lsof` 再回落到 pid 文件。
- **前端直接起 `node_modules/.bin/vite`，不走 `npm run dev`。** npm 会多套一层壳进程，
  pid 文件记的是壳的号；停服务时杀掉壳、真正监听端口的 node 还活着，只能靠端口兜底补救。
- **后端 jar 不存在时自动 `mvn package`，启动前复制到 `~/.regent/app/regent-boot.jar`。**
  首次克隆后直接 `start-all.sh` 也能跑起来。不直接运行 `target/` 里的构建产物，
  是因为下一次 `mvn clean` 会删掉 JVM 正在使用的 fat jar，导致类加载和 ServiceLoader
  在运行中途随机失败（典型表现：发验证码报「服务端未预期错误」）。
- **`restart.sh` 先构建后端再停服务。** 构建失败时旧实例继续运行，避免源码已改但后端仍跑旧 jar，
  也避免构建失败后本地服务处于停止状态。
- 数据库凭据读 `regent-boot/src/main/resources/application-local.yml`（已 gitignore），
  也可用 `REGENT_DB_USERNAME` / `REGENT_DB_PASSWORD` 覆盖。
- **后端派发心跳默认关闭**（`regent.execution.dispatch.enabled: false`）。
  任务由当前访问设备的 Regent 设备代理领取并调用该设备本机 Codex。
  一旦按需灌了演示数据（项目路径就是真实仓库），心跳一开，起个服务就会真的把演示任务跑起来。
  想看自动派发再改 `true`；只验证单条任务，用界面的「派发执行」或
  `POST /api/tasks/{id}/dispatch`。远端 profile 由 `REGENT_DISPATCH_ENABLED` 控制，默认开。

依赖：JDK 17、Maven 3.9+、Node 20+、本机 MySQL（`regent` / `regent_test` 两个 schema）。

## 公网服务器：`deploy/remote/`

服务器只运行无状态中继和静态前端，不运行 Regent 后端，不接触任何设备上的 MySQL 或 Codex。

```
/root/app/regent-relay/relay/      regent-relay.jar + relay.env
/root/app/regent-relay/frontend/   dist/
/root/app/regent-relay/script/     start.sh / stop.sh / restart.sh
/root/app/regent-relay/logs/
/root/app/regent-relay/nginx/      Nginx 示例配置
```

```bash
./deploy/remote/deploy.sh
```

`deploy.sh` 是双模式入口：

- 显式传入 `-H/--host`（或设置 `REGENT_SERVER_IP`）时，委托
  `deploy/remote/deploy-local.sh` 完成本地构建、上传和公网部署。
- 不传 `-H` 且由 Regent 注入 `REGENT_DEPLOY_DIRECTORY` 时，平台会先自动创建并进入远程目录，
  再在当前服务器执行远端部署，不会再建立 SSH 连接。

也支持直接在命令行传入连接信息，且兼容 `sh` 启动：

```bash
sh deploy/remote/deploy.sh -H 1.2.3.4 -p '你的SSH密码'
bash deploy/remote/deploy.sh -H example.com -P 22 -u root -k ~/.ssh/id_rsa
```

可用参数：

| 参数 | 说明 |
|---|---|
| `-H, --host` | 服务器 IP 或域名，必填 |
| `-p, --password` | SSH 密码；会进入命令历史，生产环境优先使用密钥 |
| `-k, --key` | SSH 私钥路径，默认 `~/.ssh/id_rsa` |
| `-P, --port` | SSH 端口，默认 `22` |
| `-u, --user` | SSH 用户名，默认 `root` |
| `-r, --remote-base` | 服务器安装目录，默认 `/root/app/regent-relay` |
| `--skip-local-connector` | 只部署服务器，不重新配对本机连接器 |
| `-h, --help` | 显示帮助 |

不传参数时仍会交互询问四项连接信息，带默认值的直接回车即可：

| 输入项 | 默认 | 说明 |
|---|---|---|
| 服务器 IP | 必填 | 公网地址，填 `127.0.0.1` 会被拒绝 |
| SSH 端口 | 22 | |
| SSH 用户名 | root | |
| SSH 密码 | 空 | 隐藏输入；留空改用密钥 `~/.ssh/id_rsa` |

脚本先做一次 SSH 探活，连不通立即退出，不会先花时间构建。
非交互场景仍可用环境变量跳过询问：`REGENT_SERVER_IP`、`REGENT_SSH_PORT`、
`REGENT_SERVER_USER`、`REGENT_SERVER_PASSWORD`（密码认证）或
`REGENT_SSH_KEY`（密钥认证）。

通过 Regent 部署平台执行时，远端目录中可预先暂存：

```text
relay/regent-relay.jar.new     # 可选；存在时备份并替换线上 JAR
frontend.tar.gz                # 可选；解压后覆盖 frontend/，归档需包含 frontend/ 根目录
```

两个文件都不存在时，脚本沿用当前线上 JAR 和前端，只重启并检查 `regent-relay`。
远端目录既可以填 `/root/app/regent-relay`，也可以填父目录 `/root/app`；脚本会自动识别
实际安装目录，避免把父目录误判成中继根目录。

`deploy.sh` 会构建中继与前端，上传产物，并在首次部署时生成服务器端共享密钥。
脚本不会把密钥打印到构建日志里；从服务器读取后写入本机 `~/.regent/relay-connector.env`：

```bash
ssh root@1.2.3.4 'grep REGENT_RELAY_SHARED_SECRET /root/app/regent-relay/relay/relay.env'
```

Nginx 是自动配置的（第 7/7 步）：脚本探测服务器上的 `nginx.conf` 路径，把
`deploy/remote/nginx/regent.conf.example` 写入 `http { }` 内层（`listen 18787`、
`root` 指向 `/root/app/regent-relay/frontend/dist`、`/api/` 反代 `127.0.0.1:8788`），
写入前自动备份、写入后 `nginx -t` 校验、校验失败自动回滚；已有该配置则跳过。
服务器上若开着 firewalld 会自动放行 18787/tcp。

`deploy.sh` 的第 6/7 步会先启动远端中继，再把服务器密钥同步到本机
`~/.regent/relay-connector.env` 并强制探活连接器；连接器未上线时部署直接失败，
不会再留下一个静态页面能打开、但所有 API 都报 `RELAY_OFFLINE` 的假成功。
所以「部署完还要手工配连接器」这步已经没有了。仍需手工的只有一件事：
**云厂商安全组放行 `18787/tcp`**。

> 公网 Nginx 会拒绝 `/api/agent/**`。外部执行者协议是给本机 codex/CLI 的免登录入口，
> 只按项目标识找活，不能暴露到公网，否则会绕过账户数据隔离。本机直连
> `http://127.0.0.1:8787/api/agent/**` 不受影响。
之后手机访问 `http://<服务器IP>:18787`；本机连接器的 `REGENT_RELAY_SERVER_URL`
填同一地址。若后续套域名与 HTTPS，把连接器地址换成 `https://域名` 即可，无需改其它配置。
只想重配连接器、不重新部署前端与中继时，单独跑 `./deploy/local/connect-relay.sh`。
部署机不是跑 Regent 的那台机器时，设 `REGENT_SKIP_LOCAL_CONNECTOR=1` 跳过本机配对。

### HTTPS 子路径

浏览器只在安全上下文中允许读取当前电脑的 `127.0.0.1` 设备代理。公网使用时，可在
`~/.regent/regent-public.env` 增加两项，让部署脚本和发布中心始终按同一入口构建：

```bash
REGENT_WEB_BASE_PATH=/regent/
REGENT_PUBLIC_URL=https://你的域名/regent/
```

Nginx 需把 `/regent/api/` 转发到 `127.0.0.1:8788`，把其余 `/regent/` 指向同一份
`frontend/dist`，并保留 SPA 回退到 `/regent/index.html`。部署脚本会同时生成
`frontend/dist-root`，让旧版桌面客户端保存的 `18787` 根路径入口继续可用；
`/api/relay/**` 和 `/health` 必须继续直连中继，供本机连接器使用。
浏览器首次检测本机 Codex 时会请求“本地网络访问”权限；允许后同一站点后续可自动检测。
可直接复用 `deploy/remote/nginx/regent-subpath.conf`，其中已显式拒绝把
`/regent/api/agent/**` 暴露到公网。

安装目录默认为 `/root/app/regent-relay`，可用 `REGENT_REMOTE_BASE` 覆盖。设置页的
「安装中继器」也调用同一脚本；密码只在本机子进程环境中使用，不写数据库、不返回浏览器。
Web 安装要求 Regent 进程能访问源码仓库、Maven、Node/npm 与部署脚本；可通过
`REGENT_RELAY_INSTALL_SCRIPT` 指向实际脚本位置。

前端 `dist/` 是静态产物，没有 Node 运行时依赖；中继由 systemd 托管，开机自启、崩溃重试。
`SystemMaxUse` 之类的日志轮转不在脚本里，服务器上按需配 `journald` 即可。

桌面客户端的 `~/.regent/downloads` 还会包含一个 Runtime 增量包。发布清单中的 `runtime`
指向该文件并附带 SHA-256 与 Ed25519 签名；`deploy.sh` 会连同完整安装包和 `latest.json`
一起同步。普通桌面更新只需同步几十 KB，完整 DMG/ZIP 仅在 Shell 升级时重新发布。
