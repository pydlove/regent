package com.pydlove.regent.execution.application.query;

import com.pydlove.regent.execution.domain.repository.ExecutionControlTowerRepository;
import com.pydlove.regent.execution.domain.repository.ExecutionControlTowerRow;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 将任务与部署执行转换为统一的用户可读状态。 */
public class ExecutionControlTowerService {

    private static final Duration SLOW_AFTER = Duration.ofSeconds(15);
    private static final Duration STALLED_AFTER = Duration.ofSeconds(30);

    private final ExecutionControlTowerRepository repository;
    private final Clock clock;

    public ExecutionControlTowerService(ExecutionControlTowerRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public ExecutionControlTowerSnapshot snapshot(UserId ownerId,
                                                  ProjectId projectId,
                                                  int limit) {
        Instant now = clock.instant();
        List<ExecutionControlTowerEntry> entries = repository
                .findEntries(ownerId, projectId, Math.max(1, Math.min(limit, 200)))
                .stream()
                .map(row -> entry(row, now))
                .sorted(Comparator
                        .comparingInt(ExecutionControlTowerService::priority)
                        .thenComparing(ExecutionControlTowerEntry::lastActivityAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int active = count(entries, entry -> "RUNNING".equals(entry.businessStatus())
                || "DEPLOYING".equals(entry.businessStatus()));
        int queued = count(entries, entry -> "QUEUED".equals(entry.businessStatus())
                || "PENDING".equals(entry.businessStatus()));
        int blocked = count(entries, entry -> "BLOCKED".equals(entry.businessStatus()));
        int abnormal = count(entries, entry -> "FAILED".equals(entry.healthStatus())
                || "SLOW".equals(entry.healthStatus())
                || "STALLED".equals(entry.healthStatus())
                || "DEVICE_LOST".equals(entry.healthStatus()));
        return new ExecutionControlTowerSnapshot(
                active, queued, blocked, abnormal, entries.size(), entries);
    }

    private ExecutionControlTowerEntry entry(ExecutionControlTowerRow row, Instant now) {
        String businessStatus = normalizedStatus(row);
        String health = health(row, businessStatus, now);
        Instant lastActivity = newest(
                row.lastEventAt(), row.startedAt(), row.updatedAt(), row.finishedAt());
        boolean task = "TASK".equals(row.executionType());
        return new ExecutionControlTowerEntry(
                row.executionType(),
                row.businessId(),
                row.runId(),
                row.attemptNo(),
                row.projectId(),
                row.projectName(),
                row.name(),
                businessStatus,
                health,
                stage(row, businessStatus),
                detail(row, businessStatus),
                deviceName(row),
                row.executor(),
                failureCategory(row, health),
                row.startedAt(),
                lastActivity,
                row.finishedAt(),
                task && ("FAILED".equals(businessStatus) || "CANCELLED".equals(businessStatus)),
                "RUNNING".equals(businessStatus) || "QUEUED".equals(businessStatus),
                "BLOCKED".equals(businessStatus));
    }

    private static String normalizedStatus(ExecutionControlTowerRow row) {
        String value = row.businessStatus();
        if ("DEPLOYMENT".equals(row.executionType())) {
            if ("QUEUED".equals(value)) return "QUEUED";
            if ("RUNNING".equals(value)) return "DEPLOYING";
            if ("TIMEOUT".equals(value)) return "FAILED";
            if ("INTERRUPTED".equals(value)) return "CANCELLED";
        }
        return value == null ? "UNKNOWN" : value;
    }

    private static String health(ExecutionControlTowerRow row, String businessStatus, Instant now) {
        if (row.leaseExpiresAt() != null && !row.leaseExpiresAt().isAfter(now)) {
            return "DEVICE_LOST";
        }
        if ("FAILED".equals(businessStatus)) return "FAILED";
        if (!"RUNNING".equals(businessStatus)) return "HEALTHY";
        if ("DEPLOYMENT".equals(row.executionType())) return "HEALTHY";
        // 租约只证明设备在线，不能证明 Codex 还在推进；卡住判断仍以最后业务事件为准。
        Instant activity = newest(row.lastEventAt(), row.startedAt(), row.updatedAt());
        if (activity == null) return "UNKNOWN";
        Duration age = Duration.between(activity, now);
        if (age.compareTo(STALLED_AFTER) >= 0) return "STALLED";
        if (age.compareTo(SLOW_AFTER) >= 0) return "SLOW";
        return "HEALTHY";
    }

    private static String stage(ExecutionControlTowerRow row, String businessStatus) {
        return switch (businessStatus) {
            case "PENDING" -> "等待派发";
            case "QUEUED" -> "等待执行位";
            case "BLOCKED" -> "等待人工决策";
            case "RUNNING" -> "执行中";
            case "DEPLOYING" -> "部署中";
            case "DONE" -> "已完成";
            case "FAILED" -> "执行失败";
            case "CANCELLED" -> "已终止";
            default -> "状态未知";
        };
    }

    private static String detail(ExecutionControlTowerRow row, String businessStatus) {
        if ("PENDING".equals(businessStatus)) return "等待用户派发任务";
        if ("QUEUED".equals(businessStatus)) return "等待全局执行位或可用设备";
        if ("BLOCKED".equals(businessStatus)) return "执行暂停，等待人工批准或驳回";
        if ("DEPLOYING".equals(businessStatus)) return "正在执行部署脚本";
        if ("RUNNING".equals(businessStatus) && row.runId() == null) {
            return "执行记录尚未建立";
        }
        return row.summary() == null || row.summary().isBlank()
                ? "暂无额外说明"
                : row.summary();
    }

    private static String deviceName(ExecutionControlTowerRow row) {
        if ("DEPLOYMENT".equals(row.executionType())) return "部署执行器";
        if (row.agentName() != null && !row.agentName().isBlank()) return row.agentName();
        return "本机执行器";
    }

    private static String failureCategory(ExecutionControlTowerRow row, String health) {
        if ("DEVICE_LOST".equals(health)) return "设备不可用";
        if ("STALLED".equals(health)) return "执行疑似卡住";
        if (!"FAILED".equals(normalizedStatus(row))) return null;
        String text = (row.summary() == null ? "" : row.summary()).toLowerCase(Locale.ROOT);
        if (text.contains("codex") || text.contains("未安装")) return "Codex 不可用";
        if (text.contains("token") || text.contains("登录") || text.contains("认证")) {
            return "身份或模型异常";
        }
        if (text.contains("ssh") || text.contains("部署")) return "部署异常";
        if (text.contains("timeout") || text.contains("超时")) return "执行超时";
        if (text.contains("network") || text.contains("网络")) return "网络异常";
        if (text.contains("路径") || text.contains("workspace") || text.contains("工作区")) {
            return "工作区异常";
        }
        return row.exitCode() == null ? "未知异常" : "命令或脚本失败";
    }

    private static int priority(ExecutionControlTowerEntry entry) {
        if ("DEVICE_LOST".equals(entry.healthStatus())) return 0;
        if ("STALLED".equals(entry.healthStatus())) return 1;
        if ("SLOW".equals(entry.healthStatus())) return 2;
        if ("FAILED".equals(entry.healthStatus())) return 3;
        if ("BLOCKED".equals(entry.businessStatus())) return 4;
        if ("RUNNING".equals(entry.businessStatus()) || "DEPLOYING".equals(entry.businessStatus())) return 5;
        if ("QUEUED".equals(entry.businessStatus()) || "PENDING".equals(entry.businessStatus())) return 6;
        return 7;
    }

    private static Instant newest(Instant... values) {
        Instant result = null;
        for (Instant value : values) {
            if (value != null && (result == null || value.isAfter(result))) result = value;
        }
        return result;
    }

    private static int count(List<ExecutionControlTowerEntry> entries,
                             java.util.function.Predicate<ExecutionControlTowerEntry> predicate) {
        return (int) entries.stream().filter(predicate).count();
    }
}
