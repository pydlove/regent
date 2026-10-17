# ZooKeeper 远程只读探查

用于任务执行期间连接远程 ZooKeeper，确认连通性并浏览少量节点、数据和 stat。这个插件只读，只调用 `exists`、`get_children`、`get`，不创建、修改、删除节点，也不改 ACL。

## 硬约束

- 连接地址、认证信息、证书只能来自项目环境变量，不能写进任务、代码、日志或本插件。
- 不执行 `create`、`set`、`delete`、`rmr`、`reconfig`、`setAcl` 等写操作。
- 不默认遍历整棵树。必须设置最大深度、最大节点数、单节点子节点展示上限和数据预览上限。
- 节点数据可能包含连接串、Token、密钥或业务数据。只读取完成任务所需的最小范围，不把完整数据贴进最终答复。
- 探查脚本放 `/tmp` 的临时目录，输出文件权限设为 `600`，任务结束删除。不要把探查脚本提交进业务仓库。
- 如果任务要求把 ZooKeeper 读取能力写进项目代码，先检查项目已有客户端和配置方式；不要为了探查另造一套生产接入。

## 环境变量契约

在 Regent 的项目环境信息里配置，变量名按项目实际情况可调整，但脚本默认读取以下名称。

| 变量 | 必填 | 默认 | 说明 |
|---|---:|---|---|
| `ZOOKEEPER_CONNECT` | 是 | — | 逗号分隔的 ensemble 地址，例如 `zk-a:2181,zk-b:2181`；可带 chroot，例如 `zk-a:2181/app` |
| `ZOOKEEPER_PATH` | 否 | `/` | 从哪个节点开始浏览；如果 `ZOOKEEPER_CONNECT` 已带 chroot，这里是 chroot 内的路径 |
| `ZOOKEEPER_MAX_DEPTH` | 否 | `2` | 从起始节点向下的最大层级；`0` 表示只看起始节点 |
| `ZOOKEEPER_MAX_NODES` | 否 | `200` | 最多返回多少个节点 |
| `ZOOKEEPER_MAX_CHILDREN_PER_NODE` | 否 | `200` | 单个节点的 children 列表最多展示多少个；遍历仍受总节点数限制 |
| `ZOOKEEPER_INCLUDE_DATA` | 否 | `true` | 是否读取节点数据；只看结构时设为 `false` 可减少敏感数据暴露 |
| `ZOOKEEPER_DATA_PREVIEW_BYTES` | 否 | `2048` | 每个节点数据最多预览多少字节；二进制内容会转成 base64 |
| `ZOOKEEPER_TIMEOUT_SECONDS` | 否 | `10` | 连接和会话操作超时 |
| `ZOOKEEPER_AUTH_SCHEME` | 否 | — | 例如 `digest`；与 `ZOOKEEPER_AUTH` 必须同时设置 |
| `ZOOKEEPER_AUTH` | 否 | — | 认证内容，digest 通常是 `user:password`；不要打印或写入文件 |
| `ZOOKEEPER_USE_SSL` | 否 | `false` | 是否使用 TLS 连接 |
| `ZOOKEEPER_CAFILE` | 否 | 系统信任库 | TLS CA 证书路径 |
| `ZOOKEEPER_CERTFILE` | 否 | — | 双向 TLS 客户端证书路径 |
| `ZOOKEEPER_KEYFILE` | 否 | — | 双向 TLS 客户端私钥路径 |
| `ZOOKEEPER_VERIFY_CERTS` | 否 | `true` | TLS 证书校验，默认开启 |
| `ZOOKEEPER_RANDOMIZE_HOSTS` | 否 | `true` | 是否在 ensemble 中随机选择初始主机 |
| `ZOOKEEPER_DEBUG` | 否 | `false` | 设为 `true` 时在未预期异常上打印堆栈；不会打印凭据 |

最小示例（值是占位符，不要照抄成真实凭据）：

```text
ZOOKEEPER_CONNECT=<zk1:2181,zk2:2181,zk3:2181>
ZOOKEEPER_PATH=/services/orders
ZOOKEEPER_MAX_DEPTH=2
ZOOKEEPER_MAX_NODES=100
ZOOKEEPER_AUTH_SCHEME=digest
ZOOKEEPER_AUTH=<username:password>
```

## 执行流程

1. 只确认变量是否存在，不要打印变量值：

   ```bash
   for name in ZOOKEEPER_CONNECT; do
     [ -n "${!name:-}" ] && echo "$name=set" || echo "$name=missing"
   done
   ```

2. 对每个 ensemble 地址做 TCP 连通性检查。如果服务端开启了 4lw，可以额外执行 `ruok`；没有开启时不要把它当成故障：

   ```bash
   nc -vz -w 3 <zk-host> 2181
   echo ruok | nc -w 3 <zk-host> 2181
   ```

3. 优先复用项目已有虚拟环境；没有时在 `/tmp` 建临时环境，不要污染仓库：

   ```bash
   umask 077
   python3 -m venv /tmp/regent-zk-venv
   /tmp/regent-zk-venv/bin/pip install -q 'kazoo>=2.10,<3' \
     -i https://mirrors.aliyun.com/pypi/simple/
   ```

   如果项目已有 Python 依赖管理方式，优先使用项目方式。内网环境使用团队镜像；不要把镜像凭据写进脚本。

4. 把下面的脚本保存到 `/tmp/regent-zk-read.py`，按项目环境变量注入后运行。脚本必须先做配置校验，再连接 ZooKeeper。

## 可直接使用的只读脚本

```python
#!/usr/bin/env python3
"""Regent ZooKeeper 远程只读探查器：只做 exists/get_children/get。"""
from __future__ import annotations

import base64
import json
import os
import posixpath
import sys
import traceback
from datetime import datetime, timezone
from typing import Any

try:
    from kazoo.client import KazooClient
    from kazoo.exceptions import AuthFailedError, KazooException, NoNodeError
except ImportError:
    print("ERROR: 缺少 kazoo，请安装 kazoo>=2.10,<3", file=sys.stderr)
    raise SystemExit(2)


class ConfigError(Exception):
    pass


def env(name: str, default: str | None = None) -> str | None:
    value = os.getenv(name, default)
    return value.strip() if isinstance(value, str) else value


def required(name: str) -> str:
    value = env(name)
    if not value:
        raise ConfigError(f"缺少环境变量 {name}")
    return value


def positive_int(name: str, default: int) -> int:
    raw = env(name)
    if not raw:
        return default
    try:
        value = int(raw)
    except ValueError as exc:
        raise ConfigError(f"{name} 必须是整数：{raw}") from exc
    if value <= 0:
        raise ConfigError(f"{name} 必须大于 0：{value}")
    return value


def non_negative_int(name: str, default: int) -> int:
    raw = env(name)
    if not raw:
        return default
    try:
        value = int(raw)
    except ValueError as exc:
        raise ConfigError(f"{name} 必须是整数：{raw}") from exc
    if value < 0:
        raise ConfigError(f"{name} 不能小于 0：{value}")
    return value


def positive_float(name: str, default: float) -> float:
    raw = env(name)
    if not raw:
        return default
    try:
        value = float(raw)
    except ValueError as exc:
        raise ConfigError(f"{name} 必须是数字：{raw}") from exc
    if value <= 0:
        raise ConfigError(f"{name} 必须大于 0：{value}")
    return value


def bool_env(name: str, default: bool) -> bool:
    raw = env(name)
    if not raw:
        return default
    normalized = raw.lower()
    if normalized in {"1", "true", "yes", "on"}:
        return True
    if normalized in {"0", "false", "no", "off"}:
        return False
    raise ConfigError(f"{name} 必须是 true/false：{raw}")


def normalize_path(raw: str) -> str:
    if not raw.startswith("/"):
        raise ConfigError(f"ZOOKEEPER_PATH 必须以 / 开头：{raw}")
    segments = [part for part in raw.split("/") if part]
    if any(part in {".", ".."} for part in segments):
        raise ConfigError(f"ZOOKEEPER_PATH 不能包含 . 或 ..：{raw}")
    return "/" + "/".join(segments)


def preview(raw: bytes | None, limit: int) -> dict[str, Any] | None:
    if raw is None:
        return None
    cut = raw[:limit]
    truncated = len(raw) > limit
    try:
        return {"encoding": "utf-8", "data": cut.decode("utf-8"), "truncated": truncated}
    except UnicodeDecodeError:
        return {
            "encoding": "base64",
            "data": base64.b64encode(cut).decode("ascii"),
            "truncated": truncated,
        }


def iso_from_ms(milliseconds: int | None) -> str | None:
    if milliseconds is None or milliseconds < 0:
        return None
    return datetime.fromtimestamp(milliseconds / 1000, tz=timezone.utc).isoformat()


def stat_dict(stat: Any) -> dict[str, Any]:
    return {
        "czxid": getattr(stat, "czxid", None),
        "mzxid": getattr(stat, "mzxid", None),
        "ctime": iso_from_ms(getattr(stat, "ctime", None)),
        "mtime": iso_from_ms(getattr(stat, "mtime", None)),
        "version": getattr(stat, "version", None),
        "cversion": getattr(stat, "cversion", None),
        "aversion": getattr(stat, "aversion", None),
        "ephemeralOwner": getattr(stat, "ephemeralOwner", None),
        "dataLength": getattr(stat, "dataLength", None),
        "numChildren": getattr(stat, "numChildren", None),
        "pzxid": getattr(stat, "pzxid", None),
    }


def build_client(timeout: float) -> KazooClient:
    hosts = required("ZOOKEEPER_CONNECT")
    kwargs: dict[str, Any] = {
        "hosts": hosts,
        "timeout": timeout,
        "randomize_hosts": bool_env("ZOOKEEPER_RANDOMIZE_HOSTS", True),
    }
    if bool_env("ZOOKEEPER_USE_SSL", False):
        ca = env("ZOOKEEPER_CAFILE")
        cert = env("ZOOKEEPER_CERTFILE")
        key = env("ZOOKEEPER_KEYFILE")
        if bool(cert) != bool(key):
            raise ConfigError("ZOOKEEPER_CERTFILE 与 ZOOKEEPER_KEYFILE 必须同时设置")
        kwargs["use_ssl"] = True
        if ca:
            kwargs["ca"] = ca
        if cert:
            kwargs["certfile"] = cert
            kwargs["keyfile"] = key
        kwargs["verify_certs"] = bool_env("ZOOKEEPER_VERIFY_CERTS", True)
    return KazooClient(**kwargs)


def main() -> int:
    client: KazooClient | None = None
    try:
        root = normalize_path(env("ZOOKEEPER_PATH", "/") or "/")
        max_depth = non_negative_int("ZOOKEEPER_MAX_DEPTH", 2)
        max_nodes = positive_int("ZOOKEEPER_MAX_NODES", 200)
        max_children = positive_int("ZOOKEEPER_MAX_CHILDREN_PER_NODE", 200)
        include_data = bool_env("ZOOKEEPER_INCLUDE_DATA", True)
        preview_bytes = non_negative_int("ZOOKEEPER_DATA_PREVIEW_BYTES", 2048)
        timeout = positive_float("ZOOKEEPER_TIMEOUT_SECONDS", 10.0)
        scheme = env("ZOOKEEPER_AUTH_SCHEME")
        auth = env("ZOOKEEPER_AUTH")
        if bool(scheme) != bool(auth):
            raise ConfigError("ZOOKEEPER_AUTH_SCHEME 与 ZOOKEEPER_AUTH 必须同时设置")

        client = build_client(timeout)
        client.start(timeout=timeout)
        if scheme and auth:
            client.add_auth(scheme, auth)

        nodes: list[dict[str, Any]] = []
        stack: list[tuple[str, int]] = [(root, 0)]
        truncated = False

        while stack and len(nodes) < max_nodes:
            path, depth = stack.pop()
            try:
                stat = client.exists(path)
                if stat is None:
                    nodes.append({"path": path, "error": "NoNode"})
                    continue
                children = sorted(client.get_children(path))
                data = None
                if include_data:
                    raw, _ = client.get(path)
                    data = preview(raw, preview_bytes)

                nodes.append({
                    "path": path,
                    "stat": stat_dict(stat),
                    "children": children[:max_children],
                    "childrenTruncated": len(children) > max_children,
                    "data": data,
                })

                if depth < max_depth:
                    remaining = max_nodes - len(nodes) - len(stack)
                    for child in reversed(children):
                        if remaining <= 0:
                            truncated = True
                            break
                        stack.append((posixpath.join(path, child), depth + 1))
                        remaining -= 1
                elif depth == max_depth and children:
                    truncated = True
            except NoNodeError:
                nodes.append({"path": path, "error": "NoNode"})

        if stack:
            truncated = True

        result = {
            "root": root,
            "limits": {
                "maxDepth": max_depth,
                "maxNodes": max_nodes,
                "maxChildrenPerNode": max_children,
                "includeData": include_data,
                "dataPreviewBytes": preview_bytes,
            },
            "count": len(nodes),
            "truncated": truncated,
            "nodes": nodes,
        }
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except ConfigError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    except AuthFailedError:
        print("ERROR: ZooKeeper 认证失败，请检查 ZOOKEEPER_AUTH_SCHEME/ZOOKEEPER_AUTH", file=sys.stderr)
        return 3
    except (KazooException, OSError) as exc:
        print(f"ERROR: ZooKeeper 读取失败：{exc}", file=sys.stderr)
        return 3
    except KeyboardInterrupt:
        print("ERROR: 被中断", file=sys.stderr)
        return 130
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: 未预期失败：{exc}", file=sys.stderr)
        if bool_env("ZOOKEEPER_DEBUG", False):
            traceback.print_exc()
        return 3
    finally:
        if client is not None:
            try:
                client.stop()
                client.close()
            except Exception:
                pass


if __name__ == "__main__":
    raise SystemExit(main())
```

## 输出解释

- stdout 是一个 JSON 对象，包含 `root`、`limits`、`count`、`truncated` 和 `nodes`。
- `nodes[].path` 是实际读取的节点路径；`stat` 是 ZooKeeper 元数据；`children` 是节点名，不是完整路径。
- `nodes[].data` 是 `{encoding, data, truncated}`。`encoding=base64` 表示数据不是 UTF-8，不要把 base64 直接当业务文本解释。
- `truncated=true` 表示还有未展示的节点、层级或子节点；不要把它误报成完整树。
- `stat.ephemeralOwner` 非 0 表示临时节点；临时节点可能随会话结束消失，不要把一次探查结果当成永久结构。

## 故障排查

| 现象 | 优先检查 |
|---|---|
| `NoNodeError` | `ZOOKEEPER_PATH` 是否存在；`ZOOKEEPER_CONNECT` 是否带了 chroot，路径是否重复拼接 |
| `AuthFailedError` | `ZOOKEEPER_AUTH_SCHEME` 与 `ZOOKEEPER_AUTH` 是否匹配；digest 是否为 `user:password`；ACL 是否允许读取 |
| 连接超时或 `ConnectionLoss` | ensemble 地址、DNS、安全组、端口、chroot；先用 `nc` 确认 TCP 可达 |
| `SessionExpiredError` | 降低读取范围并重试一次；不要高频循环重连，避免给 ensemble 增加压力 |
| 输出过大或任务卡住 | 降低 `ZOOKEEPER_MAX_DEPTH`、`ZOOKEEPER_MAX_NODES`、`ZOOKEEPER_MAX_CHILDREN_PER_NODE` 或关闭 `ZOOKEEPER_INCLUDE_DATA` |
| TLS 握手失败 | `ZOOKEEPER_USE_SSL`、CA 文件、客户端证书和主机名校验；不要为了成功默认关闭校验 |

## 交付要求

- 说明实际读取的起始路径、节点数量、最大层级、是否截断，以及哪些子节点没有展开。
- 只引用必要的节点数据和 stat，并对敏感字段脱敏；不要在答复里粘贴完整节点内容。
- 明确本次没有创建、修改、删除节点，没有修改 ACL。
- 清理 `/tmp` 下的脚本、虚拟环境和输出文件；如果保留了输出，先确认权限为 `600` 并在任务结束时删除。
- 如果任务要求把读取逻辑写进项目代码，先检查项目已有 ZooKeeper 客户端依赖；沿用现有配置、认证、重试和测试方式，不要引入第二套客户端。
