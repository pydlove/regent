package com.pydlove.regent.deployment.domain.service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 双模式部署脚本识别与项目内路径定位。
 *
 * <p>历史配置可能仍保存为 REMOTE_SCRIPT。只要脚本同时实现 run_local/run_remote
 * 并声明 -H/--host，就说明它应由本机构建并上传；执行时自动切换到项目发布模式。</p>
 */
public final class ProjectReleaseScript {

    private static final Pattern RUN_LOCAL = Pattern.compile("(?m)^\\s*run_local\\s*\\(\\s*\\)\\s*\\{");
    private static final Pattern RUN_REMOTE = Pattern.compile("(?m)^\\s*run_remote\\s*\\(\\s*\\)\\s*\\{");
    private static final int MAX_DEPTH = 10;
    private static final long MAX_SCRIPT_BYTES = 512 * 1024;
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git", ".idea", ".venv", "venv", "node_modules", "target", "build", "dist",
            "artifacts", "__pycache__");

    private ProjectReleaseScript() {
    }

    public static boolean isDualMode(String scriptContent) {
        if (scriptContent == null || scriptContent.isBlank()) {
            return false;
        }
        if (scriptContent.contains("deploy-local.sh") && scriptContent.contains("REGENT_SERVER_IP")) {
            return true;
        }
        return RUN_LOCAL.matcher(scriptContent).find()
                && RUN_REMOTE.matcher(scriptContent).find()
                && (scriptContent.contains("-H/--host") || scriptContent.contains("--host"));
    }

    /**
     * 在项目目录中查找与上传内容完全一致的同名脚本。
     *
     * @return 项目相对路径；未找到、读取失败或参数无效时返回 null
     */
    public static String resolvePath(Path projectRoot,
                                     String scriptFilename,
                                     String scriptContent) {
        if (projectRoot == null || scriptFilename == null || scriptFilename.isBlank()
                || scriptContent == null || scriptContent.isBlank()) {
            return null;
        }
        Path root = projectRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return null;
        }
        byte[] expected = scriptContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (expected.length > MAX_SCRIPT_BYTES) {
            return null;
        }
        List<Path> matches = new ArrayList<>();
        try {
            Files.walkFileTree(root, Set.of(), MAX_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (!directory.equals(root)
                            && IGNORED_DIRECTORIES.contains(directory.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (file.getFileName().toString().equals(scriptFilename)
                            && attributes.isRegularFile()
                            && attributes.size() == expected.length
                            && sameContent(file, expected)) {
                        matches.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            return null;
        }
        return matches.stream()
                .min(Comparator.comparingInt(path -> path.getNameCount()))
                .map(path -> root.relativize(path).toString().replace('\\', '/'))
                .orElse(null);
    }

    private static boolean sameContent(Path file, byte[] expected) {
        try {
            return Arrays.equals(Files.readAllBytes(file), expected);
        } catch (IOException ignored) {
            return false;
        }
    }
}
