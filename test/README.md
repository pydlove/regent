# Regent 系统测试用例样例

这些样例不依赖额外的 npm 或 Maven 包，可直接上传到平台的「测试用例」菜单。
每个脚本都用退出码表示结果：`0` 为通过，非 `0` 为失败。

## 上传与运行

1. 在「测试用例」页新建用例，选择 Web 或接口类型。
2. 上传对应脚本文件。
3. 填写下表中的执行命令。
4. 在项目环境信息中配置目标地址；不配置时默认访问本机。
5. 把用例绑定到任务。任务成功完成后，平台会在项目目录执行命令，并把脚本路径放进 `REGENT_TEST_FILE`。

## Web 用例

| 文件 | 建议命令 | 环境变量 | 覆盖内容 |
|---|---|---|---|
| `web/app-shell-smoke.mjs` | `node "$REGENT_TEST_FILE"` | `WEB_BASE_URL` | 首页、任务、测试用例、帮助路由的 HTML 外壳 |
| `web/api-proxy-smoke.mjs` | `node "$REGENT_TEST_FILE"` | `WEB_BASE_URL` | 前端 `/api` 代理与外部执行者协议 |

`WEB_BASE_URL` 默认是 `http://127.0.0.1:12580`。生产环境建议配置为实际访问地址。

## 接口用例

| 文件 | 建议命令 | 环境变量 | 覆盖内容 |
|---|---|---|---|
| `interface/health-api.sh` | `sh "$REGENT_TEST_FILE"` | `API_BASE_URL` | `/actuator/health` 返回 `UP` |
| `interface/public-api.sh` | `sh "$REGENT_TEST_FILE"` | `API_BASE_URL` | 外部执行者 JSON / Markdown 协议 |
| `interface/auth-guard.sh` | `sh "$REGENT_TEST_FILE"` | `API_BASE_URL` | 未登录访问管理接口返回 401 |

`API_BASE_URL` 默认是 `http://127.0.0.1:8787`。

## 本地一次跑完

前后端启动后执行：

```bash
bash test/run-all.sh
```

这些样例是系统级冒烟测试，不修改业务数据。需要账号登录后才能验证的接口，应另外在项目环境中提供测试 Cookie 或专用测试账号。
