package com.pydlove.regent.project.application.query;

import com.pydlove.regent.shared.domain.DomainException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 目录浏览用例 —— 给「注册项目」的路径字段做选择器，让人不必手打一整条路径。
 *
 * <p>和 {@code ProjectRegistrationService} 里的 isDirectory 校验同一条理由：这是对本机文件系统的
 * 一次读，不是要适配的外部系统（对比 codex、Git 这类真正的端口）。为一次目录列举造端口，
 * 只会让「谁在碰文件系统」变得模糊 —— 现在只有这两处，一眼看得完。
 *
 * <p>后端和 codex 跑在同一台机器上，所以这里看到的就是执行时的工作目录，
 * 不存在「选的是另一台机器的目录」这种错位。
 */
public class DirectoryListingService {

    /**
     * 一次最多返回多少个子目录。
     *
     * <p>挑工作区的人不需要看第 501 个（那个位置通常是 node_modules 或者 dist 之类）；
     * 到那个规模该用搜索，不该用列表。真截断了就说出来，不假装列全了。
     */
    private static final int MAX_ENTRIES = 500;

    /** 按名字排、大小写不敏感：Finder 就是这么排的，Linux 上全小写时结果也一致 */
    private static final Comparator<Path> BY_NAME =
            Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER);

    /** 不传路径就从主目录开始 —— 人挑工作区几乎都在自己家目录下，落到 / 只会让他多点几次 */
    public DirectoryListing list(String requestedPath) {
        Path directory = resolve(requestedPath);

        List<Path> children;
        try (Stream<Path> stream = Files.list(directory)) {
            children = stream
                    .filter(Files::isDirectory)
                    // 隐藏目录按 Finder 的默认来：不显示。codex 的工作区不会是 .git 这类目录
                    .filter(child -> !child.getFileName().toString().startsWith("."))
                    .sorted(BY_NAME)
                    .toList();
        } catch (IOException e) {
            throw new DomainException("读不到这个目录：" + directory + "（" + e.getMessage() + "）");
        }

        List<DirectoryListing.Entry> entries = children.stream()
                .limit(MAX_ENTRIES)
                .map(child -> new DirectoryListing.Entry(child.getFileName().toString(), child.toString()))
                .toList();
        return new DirectoryListing(directory.toString(), parentOf(directory),
                entries, children.size() > MAX_ENTRIES);
    }

    private static Path resolve(String requestedPath) {
        Path path;
        if (requestedPath == null || requestedPath.isBlank()) {
            path = Path.of(System.getProperty("user.home"));
        } else {
            try {
                path = Path.of(requestedPath.trim());
            } catch (InvalidPathException e) {
                throw new DomainException("这不是一个合法路径：" + requestedPath);
            }
        }
        if (!path.isAbsolute()) {
            throw new DomainException("要填绝对路径：" + requestedPath);
        }
        // normalize 只在字符串层面化简（/a/./b/../c → /a/c），不碰磁盘，省一次 IO
        Path normalized = path.normalize();
        if (!Files.isDirectory(normalized)) {
            throw new DomainException("目录不存在或不是目录：" + normalized);
        }
        return normalized;
    }

    /** 根目录没有上一级。返回 null 而不是它自己，免得界面上「上一级」原地打转 */
    private static String parentOf(Path directory) {
        Path parent = directory.getParent();
        return parent == null ? null : parent.toString();
    }
}
