package com.pydlove.regent.deployment.domain.service;

import com.pydlove.regent.deployment.domain.model.DeploymentScriptIssue;
import com.pydlove.regent.shared.domain.DomainException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 部署脚本的保守静态检查。
 *
 * <p>这里不执行 shell，也不尝试模拟 Shell 语法；只拦截会导致后台部署卡住或绕过平台连接配置的
 * 已知写法。误报的代价是让用户改脚本，漏报的代价可能是任务永久等待，因此规则宁可严格。
 */
public final class DeploymentScriptValidator {

    private static final Pattern BASH_SHEBANG = Pattern.compile(
            "^#!\\s*(?:/usr/bin/env\\s+bash|/bin/bash)(?:\\s.*)?$");
    private static final Pattern FLAG_TOKEN = Pattern.compile("(?:^|\\s)-([A-Za-z]+)");
    private static final Pattern HARD_CODED_DIRECTORY = Pattern.compile(
            "(?:^|[;&|]\\s*)(?:cd|pushd)\\s+[\"']?(?:/|~(?:/|$))");
    private static final Pattern INTERACTIVE_READ = Pattern.compile("\\bread\\s+-[A-Za-z]*p\\b");
    private static final Pattern SUDO_WITHOUT_NON_INTERACTIVE = Pattern.compile("\\bsudo\\b");
    private static final Pattern SUDO_NON_INTERACTIVE = Pattern.compile(
            "\\bsudo\\b[^#]*\\s-n(?:\\s|[)\"']|$)");
    private static final Pattern REMOTE_COMMAND = Pattern.compile(
            "(?:^|[;&|]\\s*)(?:command\\s+)?(?:ssh|scp|sftp)\\s+");

    private DeploymentScriptValidator() {
    }

    public static List<DeploymentScriptIssue> validate(String script) {
        if (script == null || script.isBlank()) {
            return List.of(new DeploymentScriptIssue(1, "EMPTY_SCRIPT", "部署脚本不能为空"));
        }

        String[] lines = script.split("\\R", -1);
        List<DeploymentScriptIssue> issues = new ArrayList<>();
        int firstContentLine = firstContentLine(lines);
        if (firstContentLine == 0
                || !BASH_SHEBANG.matcher(lines[firstContentLine - 1].strip()).matches()) {
            issues.add(new DeploymentScriptIssue(
                    Math.max(firstContentLine, 1),
                    "BASH_SHEBANG",
                    "脚本第一行必须是 #!/usr/bin/env bash 或 #!/bin/bash"));
        }

        StrictMode strictMode = strictMode(lines);
        if (!strictMode.valid()) {
            issues.add(new DeploymentScriptIssue(
                    1,
                    "STRICT_MODE",
                    "脚本必须启用失败退出与未定义变量保护，推荐 set -Eeuo pipefail"));
        }

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int lineNumber = index + 1;
            if (HARD_CODED_DIRECTORY.matcher(line).find()) {
                issues.add(new DeploymentScriptIssue(
                        lineNumber,
                        "HARD_CODED_DIRECTORY",
                        "不要写死远程目录；平台会先进入配置的目录，必要时使用 \"$REGENT_DEPLOY_DIRECTORY\""));
            }
            if (INTERACTIVE_READ.matcher(line).find()) {
                issues.add(new DeploymentScriptIssue(
                        lineNumber,
                        "INTERACTIVE_READ",
                        "不能使用 read -p 等待输入；部署脚本必须完全非交互"));
            }
            if (SUDO_WITHOUT_NON_INTERACTIVE.matcher(line).find()
                    && !SUDO_NON_INTERACTIVE.matcher(line).find()) {
                issues.add(new DeploymentScriptIssue(
                        lineNumber,
                        "INTERACTIVE_SUDO",
                        "sudo 必须使用 -n 非交互模式，并提前在服务器配置 NOPASSWD"));
            }
            if (REMOTE_COMMAND.matcher(line).find()) {
                issues.add(new DeploymentScriptIssue(
                        lineNumber,
                        "HARD_CODED_REMOTE_CONNECTION",
                        "不要在部署脚本里再次执行 ssh/scp/sftp；服务器连接由平台配置"));
            }
        }
        return List.copyOf(issues);
    }

    public static void requireValid(String script) {
        List<DeploymentScriptIssue> issues = validate(script);
        if (issues.isEmpty()) {
            return;
        }
        StringBuilder message = new StringBuilder("部署脚本不符合规范：");
        for (DeploymentScriptIssue issue : issues) {
            message.append("\n第 ").append(issue.line()).append(" 行：").append(issue.message());
        }
        throw new DomainException(message.toString());
    }

    private static int firstContentLine(String[] lines) {
        for (int index = 0; index < lines.length; index++) {
            if (!lines[index].isBlank()) {
                return index + 1;
            }
        }
        return 0;
    }

    private static StrictMode strictMode(String[] lines) {
        boolean errexit = false;
        boolean nounset = false;
        boolean pipefail = false;
        for (String raw : lines) {
            String line = raw.strip();
            if (!line.startsWith("set ")) {
                continue;
            }
            if (line.matches(".*\\bpipefail\\b.*")) {
                pipefail = true;
            }
            Matcher flags = FLAG_TOKEN.matcher(line.substring(4));
            while (flags.find()) {
                String value = flags.group(1);
                errexit |= value.contains("e");
                nounset |= value.contains("u");
            }
        }
        return new StrictMode(errexit && nounset && pipefail);
    }

    private record StrictMode(boolean valid) {
    }
}
