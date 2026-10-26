# Regent Desktop

轻量 Electron 桌面壳。客户端不保存业务数据，默认安装会自动连接
`https://ichuang.top/regent/decisions`，之后直接打开同一个 Web 工作台；自建服务可在菜单中切换。

## 本地运行

```bash
npm install --registry=https://registry.npmmirror.com --strict-ssl=false
npm start
```

## 构建发布包

```bash
npm run build:release
```

产物：

- `dist/Regent-<版本>-<发布码>-macOS.dmg`：Apple Silicon 与 Intel 通用应用；
- `dist/Regent-<版本>-<发布码>-Windows-x64.zip`：Windows 10/11 x64 便携版，解压后运行 `Regent.exe`。

Windows 包同时提供：

- `Start-Device-Agent.cmd`：不打开主窗口，只启动本机设备代理。
- `Install-Device-Agent-Autostart.cmd`：写入当前用户的 Startup 快捷方式，登录 Windows 后自动启动代理。
- `Uninstall-Device-Agent-Autostart.cmd`：移除快捷方式并停止代理进程。

构建包使用临时签名，未使用 Apple Developer ID 或 Windows 代码签名证书。macOS 首次启动需要在
Finder 中右键应用并选择“打开”；正式对外分发前应补签名与公证。

## 增量更新

桌面端采用固定 Shell + 版本化 Runtime：

- Shell 包含 Electron、`bootstrap.js`、签名公钥和内置 Runtime，只随 Electron、原生依赖或
  启动器升级；
- Runtime 包含 `main.js`、设备代理、配置窗口和更新窗口，普通功能更新只发布 Runtime；
- Runtime 下载后同时校验 SHA-256 与 Ed25519 签名，然后解压到
  `<userData>/runtime/releases/<版本>`，不再改写已安装应用；
- 新 Runtime 启动后必须在 20 秒内确认健康，否则下一次启动自动回滚上一版本或内置 Runtime。
- 启动失败会记录失败发布码，同一版本不再重复弹窗，只有新的发布码才会重新提示。
- 更新提示支持“跳过此版本”“退出应用后升级”“现在升级”。跳过的发布码保存在本机，
  新发布码仍会正常提示；延迟升级会在当前会话退出后应用。
- 设备代理每次进程启动生成新的实例号；重启后的第一次心跳会让平台立即收敛旧租约并把任务重新排队。

只构建 Runtime：

```bash
REGENT_RELEASE_CODE=R100B001 npm run build:runtime
```

产物为 `dist/Regent-<版本>-<发布码>-runtime.zip`，当前通常小于 100 KB。

发布脚本默认生成 Runtime 增量包；只有显式传入新的 Shell 发布号时才重新生成完整安装包：

```bash
node scripts/release-version.mjs \
  --version Regent_V1.0.3_R100B001 \
  --notes "修复若干问题" \
  --no-tag

node scripts/release-version.mjs \
  --version Regent_V1.0.3_R100B020 \
  --shell-release R100B020 \
  --notes "升级 Electron 运行时" \
  --no-tag
```

只修改网页或后端时使用 `--web-only`。浏览器会提示刷新，桌面客户端不会弹出升级框：

```bash
node scripts/release-version.mjs \
  --version Regent_V1.0.3_R100B002 \
  --web-only \
  --notes "仅更新网页与后端" \
  --no-tag
```

首次启用签名机制或轮换密钥时：

```bash
cd regent-desktop
node scripts/init-runtime-signing.mjs --public-file runtime-public-key.pem
```

私钥默认写入 `~/.regent/runtime-signing-private.pem`，不得入库；CI 可通过
`REGENT_RUNTIME_SIGNING_KEY` 传入私钥文件路径或 PEM 内容。公钥随完整客户端发布，
轮换公钥必须发布新的 Shell。

旧客户端仍会忽略 `latest.json.runtime` 并继续使用完整安装包。客户端启动后会读取服务端
`latest.json`，优先安装兼容的小体积 Runtime；只有 Runtime 缺失或要求更高 Shell 时才下载完整客户端。
