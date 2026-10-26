# Regent Mobile P0

Regent 手机 App 的 Capacitor 技术验证工程。目标是先证明手机认证、安全存储、多账号隔离和真实业务接口可用，再进入完整的远程任务操作。

## 当前范围

- Vue 3 + TypeScript + Capacitor 8。
- iOS 15+、Android 9+（minSdk 28），仅竖屏。
- 手机独立 Access Token / Refresh Token，不复用浏览器 Cookie。
- iOS Keychain / Android Keystore 安全存储；Web 调试只使用 `sessionStorage`。
- 面容 ID、触控 ID、指纹或系统密码解锁。
- A/B 账号缓存隔离，切换账号时清除令牌、任务、项目和设备缓存。
- 只读展示主机状态、项目、运行中任务和待处理决策。

本阶段明确不做：新建任务、决策批准、二维码配对、推送和文件上传。这些属于后续 P1/P2，不能在认证底座未验收前继续堆页面。

## 启动

```bash
cd regent-mobile
npm install
npm run dev
```

Web 调试地址：`http://127.0.0.1:12590`。Vite 将 `/api` 代理到 `http://127.0.0.1:8787`，可通过 `REGENT_API_TARGET` 修改。

## 原生运行

原生包必须配置可访问的 API 域名：

```bash
VITE_API_BASE_URL=https://regent.example.com npm run cap:sync
```

本地调试 Android 模拟器时可使用 `http://10.0.2.2:8787`，调试 Manifest 已允许明文 HTTP；正式包只应使用 HTTPS。

```bash
npm run build
npx cap sync
npx cap open android
npx cap open ios
```

当前开发机已具备 JDK 21 与 Android SDK，但未安装 Xcode。Android 调试包可在本机构建；
iOS 真机安装与 TestFlight 必须在具备 Xcode 和 Apple Developer 签名环境的机器上完成。

P0 已使用 JDK 21、Android SDK 36 和 Build Tools 35 成功生成调试 APK。
Android 构建使用 `android/init.gradle` 的镜像仓库规避国内网络证书问题：

```bash
cd android
JAVA_HOME=/path/to/jdk-21 ANDROID_HOME=/path/to/android-sdk \
  ./gradlew --no-daemon -I init.gradle assembleDebug
```

产物位于 `android/app/build/outputs/apk/debug/app-debug.apk`。该包使用 Android 调试签名，
只用于 P0 内测；正式发布前必须配置独立、可长期保存的发布签名。iOS 仍需要 Xcode、Apple
Developer 账号和 TestFlight/App Store 签名流程。

供 Regent 客户端跳转的公开下载页为 `/download/app`。发布后把 Android APK 与 iOS
TestFlight/App Store 链接写入 `VITE_DOWNLOAD_ANDROID_URL` / `VITE_DOWNLOAD_IOS_URL`，
或写入发布清单的 `downloads.android` / `downloads.ios`。

## API

```text
POST /api/mobile/auth/login
POST /api/mobile/auth/refresh
POST /api/mobile/auth/logout
GET  /api/devices/current
GET  /api/projects
GET  /api/tasks/stats
GET  /api/tasks?status=RUNNING&page=1&size=5
GET  /api/decisions?scope=PENDING&page=1&size=5
```

手机会话表为 `mobile_auth_session`。Access Token 30 分钟，Refresh Token 30 天并轮换；任意已使用的刷新令牌再次出现时，整组手机安装会话立即撤销。

## 安全约束

- 明文令牌只进入 Keychain/Keystore，数据库只保存 SHA-256。
- Refresh Token 每次使用后轮换，刷新请求在数据库事务中锁行。
- 同一安装重新登录会撤销旧令牌；同一账号的其他手机不受影响。
- 切换账号必须清除旧账号业务缓存，不允许跨账号复用页面状态。
- Capacitor 固定来源通过 CORS 白名单访问 `/api/**`，不开放通配来源，也不允许 Cookie。
- 日志、界面和错误消息不得输出完整 Token、密码或业务敏感数据。

## 验证

```bash
npm test
npm run build
```

原生平台同步：

```bash
npm run cap:sync
```
