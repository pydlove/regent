# 内置插件

本目录由 `regent-infrastructure` 在 Maven 打包时复制到 classpath 的
`builtin-plugins/`，应用启动后自动同步到插件库。运行时只读取 JAR 内资源，
不依赖仓库在当前机器上的路径。

## 目录格式

每个插件使用独立目录：

```text
plugins/
  engineering-delivery-protocol/
    manifest.properties
    content.md
```

`manifest.properties` 使用 UTF-8 编码：

```properties
key=engineering-delivery-protocol
name=工程交付协议
description=一句话说明这个插件解决什么问题
tags=通用,交付,质量
filename=engineering-delivery-protocol.md
content-file=content.md
enabled=true
```

- `key` 是稳定标识，发布后不要修改，否则会被当成一个新插件。
- `filename` 是任务执行时看到的文件名，不能包含路径分隔符。
- `content-file` 只能是同目录文件名。
- `enabled` 只决定首次同步时的默认状态；之后用户在页面上的启停选择会保留。
- 修改正文后重启应用会生成新版本；已经绑定旧版本的任务不受影响。
- 从目录删除插件不会删除数据库记录，避免破坏历史任务绑定。需要下线时在页面停用。

不要把密码、Token、私钥或其他凭据写进插件。凭据应由环境信息在运行时注入。
