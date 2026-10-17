# Kafka 远程只读探查

用于任务执行期间连接远程 Kafka，确认连通性并读取少量消息样本。这个插件只读，不创建主题、不生产消息、不修改 ACL，也不提交消费位点。

## 硬约束

- 连接地址、账号、密码、证书只能来自项目环境变量，不能写进任务、代码、日志或本插件。
- 不执行 `kafka-topics --create/--alter/--delete`、`kafka-acls`、`kafka-configs --alter` 等写操作。
- 不调用 `consumer.subscribe()`，不加入生产消费组，不提交 offset。脚本使用 `assign()` 直接按分区读取一个受限窗口。
- 默认只读每个分区尾部 `10` 条、总计最多 `50` 条，不能无界拉取。读取历史数据前先确认数据量和敏感性。
- 输出只保留 key/value 的短预览。不要把完整消息、Token、手机号、身份证号或业务敏感字段贴进最终答复。
- 探查脚本放 `/tmp` 的临时目录，输出文件权限设为 `600`，任务结束删除。不要把探查脚本提交进业务仓库。
- 如果任务要求把 Kafka 读取能力写进项目代码，先检查项目已有客户端和配置方式；不要为了探查另造一套生产接入。

## 环境变量契约

在 Regent 的项目环境信息里配置，变量名按项目实际情况可调整，但脚本默认读取以下名称。

| 变量 | 必填 | 默认 | 说明 |
|---|---:|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | 是 | — | 逗号分隔的 broker 地址，例如 `broker-a:9092,broker-b:9092`，不要填 ZooKeeper 地址 |
| `KAFKA_TOPICS` | 是 | — | 逗号分隔的精确主题名，不支持正则；先用最少的主题完成探查 |
| `KAFKA_SECURITY_PROTOCOL` | 否 | `PLAINTEXT` | `PLAINTEXT`、`SSL`、`SASL_PLAINTEXT`、`SASL_SSL` |
| `KAFKA_SASL_MECHANISM` | 否 | `PLAIN` | SASL 时使用；支持 `PLAIN`、`SCRAM-SHA-256`、`SCRAM-SHA-512` |
| `KAFKA_SASL_USERNAME` | SASL 时是 | — | SASL 用户名 |
| `KAFKA_SASL_PASSWORD` | SASL 时是 | — | SASL 密码 |
| `KAFKA_SSL_CAFILE` | 否 | 系统信任库 | CA 证书路径 |
| `KAFKA_SSL_CERTFILE` | 否 | — | 双向 TLS 客户端证书路径 |
| `KAFKA_SSL_KEYFILE` | 否 | — | 双向 TLS 客户端私钥路径 |
| `KAFKA_SSL_CHECK_HOSTNAME` | 否 | `true` | 默认校验证书主机名，只有明确知道证书不含主机名时才临时关闭 |
| `KAFKA_START_MODE` | 否 | `tail` | `tail` 读每个分区最后若干条；`beginning` 从分区起点开始，但仍受条数上限约束 |
| `KAFKA_MAX_MESSAGES` | 否 | `50` | 总消息上限 |
| `KAFKA_MAX_MESSAGES_PER_PARTITION` | 否 | `10` | 单分区上限 |
| `KAFKA_TIMEOUT_SECONDS` | 否 | `20` | 总读取时间上限，超时后停止并输出已读样本 |
| `KAFKA_VALUE_PREVIEW_CHARS` | 否 | `2000` | key/value/header 的预览字符数；二进制内容会转成 base64 |
| `KAFKA_MAX_POLL_RECORDS` | 否 | `200` | 单次 poll 最大记录数，用于避免一次拉回过大 |
| `KAFKA_REQUEST_TIMEOUT_MS` | 否 | `15000` | 单次 broker 请求超时 |
| `KAFKA_API_TIMEOUT_MS` | 否 | `10000` | 自动探测 broker API 版本的超时 |
| `KAFKA_CLIENT_ID` | 否 | `regent-read-<pid>` | 客户端标识，便于 broker 侧审计 |
| `KAFKA_DEBUG` | 否 | `false` | 设为 `true` 时在未预期异常上打印堆栈；不会打印凭据 |

最小示例（值是占位符，不要照抄成真实凭据）：

```text
KAFKA_BOOTSTRAP_SERVERS=<broker1:9092,broker2:9092>
KAFKA_TOPICS=<orders,payments>
KAFKA_SECURITY_PROTOCOL=SASL_SSL
KAFKA_SASL_MECHANISM=SCRAM-SHA-512
KAFKA_SASL_USERNAME=<username>
KAFKA_SASL_PASSWORD=<password>
KAFKA_SSL_CAFILE=<path/to/ca.pem>
KAFKA_MAX_MESSAGES=30
```

## 执行流程

1. 只确认变量是否存在，不要打印变量值：

   ```bash
   for name in KAFKA_BOOTSTRAP_SERVERS KAFKA_TOPICS; do
     [ -n "${!name:-}" ] && echo "$name=set" || echo "$name=missing"
   done
   ```

2. 对每个 broker 做 TCP 连通性检查。TLS/SASL 握手由脚本完成，`nc` 成功只代表端口可达：

   ```bash
   nc -vz -w 3 <broker-host> <port>
   ```

3. 优先复用项目已有虚拟环境；没有时在 `/tmp` 建临时环境，不要污染仓库：

   ```bash
   umask 077
   python3 -m venv /tmp/regent-kafka-venv
   /tmp/regent-kafka-venv/bin/pip install -q 'kafka-python>=2.0,<3' \
     -i https://mirrors.aliyun.com/pypi/simple/
   ```

   如果项目已有 Python 依赖管理方式，优先使用项目方式。内网环境使用团队镜像；不要把镜像凭据写进脚本。

4. 把下面的脚本保存到 `/tmp/regent-kafka-read.py`，按项目环境变量注入后运行。脚本必须先做配置校验，再连接 broker。

## 可直接使用的只读脚本

```python
#!/usr/bin/env python3
"""Regent Kafka 远程只读采样器：按分区读取有界窗口，不加入消费组、不提交位点。"""
from __future__ import annotations

import base64
import json
import os
import sys
import time
import traceback
from datetime import datetime, timezone
from typing import Any

try:
    from kafka import KafkaConsumer, TopicPartition
    from kafka.errors import KafkaError
except ImportError:
    print("ERROR: 缺少 kafka-python，请安装 kafka-python>=2.0,<3", file=sys.stderr)
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


def iso_timestamp(milliseconds: int | None) -> str | None:
    if milliseconds is None or milliseconds < 0:
        return None
    return datetime.fromtimestamp(milliseconds / 1000, tz=timezone.utc).isoformat()


def build_consumer() -> KafkaConsumer:
    protocol = (env("KAFKA_SECURITY_PROTOCOL", "PLAINTEXT") or "PLAINTEXT").upper()
    if protocol not in {"PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL"}:
        raise ConfigError(f"不支持的 KAFKA_SECURITY_PROTOCOL：{protocol}")

    brokers = [part.strip() for part in required("KAFKA_BOOTSTRAP_SERVERS").split(",") if part.strip()]
    if not brokers:
        raise ConfigError("KAFKA_BOOTSTRAP_SERVERS 没有有效地址")

    config: dict[str, Any] = {
        "bootstrap_servers": brokers,
        "client_id": env("KAFKA_CLIENT_ID") or f"regent-read-{os.getpid()}",
        "security_protocol": protocol,
        "enable_auto_commit": False,
        "request_timeout_ms": positive_int("KAFKA_REQUEST_TIMEOUT_MS", 15000),
        "api_version_auto_timeout_ms": positive_int("KAFKA_API_TIMEOUT_MS", 10000),
        "max_poll_records": positive_int("KAFKA_MAX_POLL_RECORDS", 200),
    }

    if protocol in {"SASL_PLAINTEXT", "SASL_SSL"}:
        mechanism = (env("KAFKA_SASL_MECHANISM", "PLAIN") or "PLAIN").upper()
        if mechanism not in {"PLAIN", "SCRAM-SHA-256", "SCRAM-SHA-512"}:
            raise ConfigError(f"脚本未覆盖的 SASL 机制：{mechanism}")
        config["sasl_mechanism"] = mechanism
        config["sasl_plain_username"] = required("KAFKA_SASL_USERNAME")
        config["sasl_plain_password"] = required("KAFKA_SASL_PASSWORD")

    if protocol in {"SSL", "SASL_SSL"}:
        ca = env("KAFKA_SSL_CAFILE")
        cert = env("KAFKA_SSL_CERTFILE")
        key = env("KAFKA_SSL_KEYFILE")
        if bool(cert) != bool(key):
            raise ConfigError("KAFKA_SSL_CERTFILE 与 KAFKA_SSL_KEYFILE 必须同时设置")
        if ca:
            config["ssl_cafile"] = ca
        if cert:
            config["ssl_certfile"] = cert
            config["ssl_keyfile"] = key
        config["ssl_check_hostname"] = bool_env("KAFKA_SSL_CHECK_HOSTNAME", True)

    return KafkaConsumer(**config)


def main() -> int:
    consumer: KafkaConsumer | None = None
    started = time.monotonic()
    try:
        topics = [part.strip() for part in required("KAFKA_TOPICS").split(",") if part.strip()]
        if not topics:
            raise ConfigError("KAFKA_TOPICS 没有有效主题名")

        max_total = positive_int("KAFKA_MAX_MESSAGES", 50)
        per_partition = positive_int("KAFKA_MAX_MESSAGES_PER_PARTITION", 10)
        timeout_seconds = positive_int("KAFKA_TIMEOUT_SECONDS", 20)
        preview_chars = non_negative_int("KAFKA_VALUE_PREVIEW_CHARS", 2000)
        start_mode = (env("KAFKA_START_MODE", "tail") or "tail").lower()
        if start_mode not in {"tail", "beginning"}:
            raise ConfigError("KAFKA_START_MODE 只能是 tail 或 beginning")

        consumer = build_consumer()
        deadline = time.monotonic() + timeout_seconds
        total = 0
        stop_reason = "no-readable-partitions"

        for topic in topics:
            partitions = consumer.partitions_for_topic(topic)
            if not partitions:
                print(json.dumps({
                    "type": "error",
                    "topic": topic,
                    "message": "主题不存在，或当前账号看不到该主题的分区",
                }, ensure_ascii=False))
                continue

            topic_partitions = [TopicPartition(topic, partition) for partition in sorted(partitions)]
            beginning = consumer.beginning_offsets(topic_partitions)
            end = consumer.end_offsets(topic_partitions)
            states: dict[TopicPartition, dict[str, int]] = {}

            consumer.assign(topic_partitions)
            for partition in topic_partitions:
                first = beginning[partition]
                last = end[partition]
                start = first if start_mode == "beginning" else max(first, last - per_partition)
                states[partition] = {"start": start, "end": last, "count": 0}
                consumer.seek(partition, start)

            while total < max_total and time.monotonic() < deadline:
                remaining_ms = max(1, min(1000, int((deadline - time.monotonic()) * 1000)))
                records = consumer.poll(timeout_ms=remaining_ms)
                if not records:
                    if all(
                        state["start"] >= state["end"]
                        or state["count"] >= per_partition
                        or (
                            consumer.position(partition)
                            if consumer.position(partition) is not None
                            else state["start"]
                        ) >= state["end"]
                        for partition, state in states.items()
                    ):
                        stop_reason = "end-of-partitions"
                        break
                    continue

                for partition, batch in records.items():
                    state = states.get(partition)
                    if state is None:
                        continue
                    for record in batch:
                        if record.offset < state["start"] or record.offset >= state["end"]:
                            continue
                        if state["count"] >= per_partition:
                            continue
                        if total >= max_total:
                            stop_reason = "max-messages"
                            break

                        headers = []
                        for header_key, header_value in (record.headers or []):
                            key_text = header_key if isinstance(header_key, str) else str(header_key)
                            headers.append({
                                "key": key_text,
                                "value": preview(header_value, preview_chars),
                            })

                        print(json.dumps({
                            "type": "message",
                            "topic": record.topic,
                            "partition": record.partition,
                            "offset": record.offset,
                            "timestamp": iso_timestamp(record.timestamp),
                            "key": preview(record.key, preview_chars),
                            "value": preview(record.value, preview_chars),
                            "headers": headers,
                        }, ensure_ascii=False))
                        total += 1
                        state["count"] += 1

                    if total >= max_total:
                        stop_reason = "max-messages"
                        break
                if total >= max_total:
                    stop_reason = "max-messages"
                    break

            if total >= max_total:
                stop_reason = "max-messages"
                break

        if stop_reason == "no-readable-partitions" and time.monotonic() >= deadline:
            stop_reason = "timeout"

        print(json.dumps({
            "type": "summary",
            "topics": topics,
            "messages": total,
            "maxMessages": max_total,
            "maxMessagesPerPartition": per_partition,
            "startMode": start_mode,
            "stopReason": stop_reason,
            "elapsedSeconds": round(time.monotonic() - started, 3),
        }, ensure_ascii=False))
        return 0
    except ConfigError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    except KafkaError as exc:
        print(f"ERROR: Kafka 读取失败：{exc}", file=sys.stderr)
        return 3
    except KeyboardInterrupt:
        print("ERROR: 被中断", file=sys.stderr)
        return 130
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: 未预期失败：{exc}", file=sys.stderr)
        if bool_env("KAFKA_DEBUG", False):
            traceback.print_exc()
        return 3
    finally:
        if consumer is not None:
            try:
                consumer.close()
            except Exception:
                pass


if __name__ == "__main__":
    raise SystemExit(main())
```

## 输出解释

- stdout 每条消息一行 JSON，`type=message`。
- 最后一行是 `type=summary`，记录读取总数、停止原因和耗时。
- 主题不可见时输出 `type=error`，不要把它当成“主题为空”；优先检查主题名、ACL 和 broker 地址。
- `key`、`value`、`headers[].value` 都是 `{encoding, data, truncated}`。`encoding=base64` 表示不是 UTF-8，不要把 base64 直接当业务文本解释。
- `stopReason=max-messages` 表示达到条数上限；`end-of-partitions` 表示已经读到每个分区当前末尾；`timeout` 表示先触发了时间上限。

## 故障排查

| 现象 | 优先检查 |
|---|---|
| `NoBrokersAvailable`、连接超时 | `KAFKA_BOOTSTRAP_SERVERS` 是否是 broker 地址而不是 ZooKeeper；DNS、安全组、端口是否可达 |
| SASL 认证失败 | `KAFKA_SECURITY_PROTOCOL`、`KAFKA_SASL_MECHANISM`、用户名密码和 broker 的 listener 配置是否匹配 |
| SSL 证书错误 | CA 文件、证书主机名、证书有效期；不要为了绕过错误默认关闭主机名校验 |
| 主题看不到或没有分区 | 主题名拼写、租户/命名空间、Describe/Read ACL；不要用管理员账号直接绕过权限 |
| 读不到消息 | `tail` 模式只读当前末尾窗口；确认分区近期有新消息，或显式使用 `beginning` 并保持小上限 |
| 消息体很大或乱码 | 降低 `KAFKA_VALUE_PREVIEW_CHARS`；二进制内容按 base64 处理，不要整段打印 |

## 交付要求

- 说明实际连接的是哪些 broker 端口、读取了哪些主题和分区、每个分区读了多少条、是否触发上限。
- 只引用必要的消息样本，并对敏感字段做脱敏；不要在答复里粘贴完整 payload。
- 明确本次没有生产消息、没有提交 offset、没有修改主题或 ACL。
- 清理 `/tmp` 下的脚本、虚拟环境和输出文件；如果保留了输出，先确认权限为 `600` 并在任务结束时删除。
- 如果任务要求把读取逻辑写进项目代码，先检查项目已有 Kafka 客户端依赖；沿用现有配置、序列化、错误处理和测试方式，不要引入第二套客户端。
