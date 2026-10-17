package com.pydlove.regent.execution.infrastructure.git;

import com.pydlove.regent.shared.domain.DomainException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.Map;

/**
 * 执行器进程级的 Git 引用守卫。
 *
 * <p>只靠提示词要求模型「不要建分支」不是硬约束。这里给子进程注入一个临时的
 * {@code reference-transaction} hook：任务执行期间，只允许更新当前任务分支的引用；
 * 新建、修改或删除其他本地分支都会被 Git 直接拒绝。
 *
 * <p>hook 与任务分支绑定，落在本次运行目录中，进程结束后删除，不修改用户仓库的
 * {@code .git/hooks}，也不会把守卫留给后续任务。
 */
public final class GitRefGuard {

    private static final String HOOK_NAME = "reference-transaction";

    private GitRefGuard() {
    }

    /**
     * 安装守卫。
     *
     * @return hooks 目录，调用方执行结束后必须传给 {@link #cleanup(Path)}
     */
    public static Path install(Path runDirectory, String allowedBranch) {
        String branch = requireSafeBranch(allowedBranch);
        try {
            Path hooks = Files.createTempDirectory(runDirectory, "git-guard-");
            Path hook = hooks.resolve(HOOK_NAME);
            Files.writeString(hook, hookScript("refs/heads/" + branch), StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(hook, PosixFilePermissions.fromString("rwx------"));
            return hooks;
        } catch (IOException | UnsupportedOperationException e) {
            throw new DomainException("无法安装 Git 分支守卫：" + e.getMessage(), e);
        }
    }

    /** 通过 Git 自带的环境配置把 hooksPath 只注入当前子进程，不改仓库配置。 */
    public static Map<String, String> environment(Path hooksDirectory) {
        return Map.of(
                "GIT_CONFIG_COUNT", "1",
                "GIT_CONFIG_KEY_0", "core.hooksPath",
                "GIT_CONFIG_VALUE_0", hooksDirectory.toString());
    }

    public static void cleanup(Path hooksDirectory) {
        if (hooksDirectory == null || !Files.exists(hooksDirectory)) {
            return;
        }
        try (var paths = Files.walk(hooksDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时目录清理失败不影响任务结论
                }
            });
        } catch (IOException ignored) {
            // 同上
        }
    }

    private static String requireSafeBranch(String branch) {
        if (branch == null || branch.isBlank()) {
            throw new DomainException("Git 分支守卫缺少任务分支名");
        }
        String value = branch.trim();
        if (!value.matches("[A-Za-z0-9._/-]+") || value.contains("..")
                || value.startsWith("/") || value.endsWith("/")) {
            throw new DomainException("任务分支名不能用于 Git 守卫：" + branch);
        }
        return value;
    }

    private static String hookScript(String allowedRef) {
        return """
                #!/bin/sh
                state="${1:-}"
                [ "$state" = "prepared" ] || exit 0
                allowed='%s'
                while IFS=' ' read -r old new ref; do
                  case "$ref" in
                    refs/heads/*)
                      if [ "$ref" != "$allowed" ]; then
                        echo "Regent: 未经人工批准，任务执行期间禁止创建、修改或删除其他分支：$ref" >&2
                        exit 1
                      fi
                      if [ "$new" = "0000000000000000000000000000000000000000" ]; then
                        echo "Regent: 任务执行期间禁止删除当前任务分支：$ref" >&2
                        exit 1
                      fi
                      ;;
                  esac
                done
                exit 0
                """.formatted(allowedRef);
    }
}
