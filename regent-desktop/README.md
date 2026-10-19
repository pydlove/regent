# Regent Desktop

轻量 Electron 桌面壳。客户端不保存业务数据，首次启动填写 Regent 服务地址，之后直接打开同一个 Web 工作台。

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

构建包使用临时签名，未使用 Apple Developer ID 或 Windows 代码签名证书。macOS 首次启动需要在
Finder 中右键应用并选择“打开”；正式对外分发前应补签名与公证。

客户端启动后会读取服务端 `latest.json`。如果发布码与当前安装包不同，会弹出“发现新版本”提示；
确认后自动下载、校验 SHA-256、替换当前客户端并重启。静默无提示安装仍需 Apple/Windows
代码签名和公证。
