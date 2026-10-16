# 外部执行者协议（入口）

这份协议的**正文只有一份**，放在
`regent-infrastructure/src/main/resources/agent/agent-protocol.md` —— 它随 jar 一起发布，
运行时的取用地址是：

```
GET http://127.0.0.1:8787/api/agent/contract.md   # 纯文本，读一次就够
GET http://127.0.0.1:8787/api/agent/contract      # 同一份协议 + 出参 JSON Schema + 端点表
```

这里刻意不放副本：两份一模一样的文档，迟早会有一份先改。

要把活交给某个模型工具时，把上面那个地址和项目标识给它，剩下的它自己会读。
