package com.pydlove.regent.project.application.query;

import com.pydlove.regent.project.application.query.DirectoryListing.Entry;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 目录浏览测试。
 *
 * <p>它守的是「选择器里看到的东西」：列出来的必须都能注册成功（所以文件、隐藏目录不进列表），
 * 读不到的必须当场报错、而不是给一个空列表 —— 空列表看起来像「这个目录是空的」，
 * 那会让人以为路径对了、只是还没建东西。
 */
class DirectoryListingServiceTest {

    private final DirectoryListingService service = new DirectoryListingService();

    private static List<String> names(DirectoryListing listing) {
        return listing.entries().stream().map(Entry::name).toList();
    }

    @Test
    @DisplayName("不传路径就从主目录开始")
    void defaultsToHomeDirectory() {
        DirectoryListing listing = service.list(null);

        assertEquals(System.getProperty("user.home"), listing.path());
        assertEquals(Path.of(System.getProperty("user.home")).getParent().toString(), listing.parent());
    }

    @Test
    @DisplayName("只列子目录：文件列进去也注册不了，只会让人白点一下")
    void listsDirectoriesOnly(@TempDir Path root) throws IOException {
        Files.createDirectory(root.resolve("alpha"));
        Files.createDirectory(root.resolve("beta"));
        Files.writeString(root.resolve("pom.xml"), "<project/>");

        DirectoryListing listing = service.list(root.toString());

        assertEquals(List.of("alpha", "beta"), names(listing));
        assertEquals(root.resolve("alpha").toString(), listing.entries().get(0).path(),
                "给前端的必须是绝对路径，别让它自己拼");
        assertFalse(listing.truncated());
    }

    @Test
    @DisplayName("隐藏目录不列，与 Finder 默认一致")
    void skipsHiddenDirectories(@TempDir Path root) throws IOException {
        Files.createDirectory(root.resolve("visible"));
        Files.createDirectory(root.resolve(".git"));

        assertEquals(List.of("visible"), names(service.list(root.toString())));
    }

    @Test
    @DisplayName("按名字排序，不区分大小写 —— 否则同一屏里大小写各排一堆")
    void sortsCaseInsensitively(@TempDir Path root) throws IOException {
        for (String name : List.of("Zebra", "apple", "Mango")) {
            Files.createDirectory(root.resolve(name));
        }

        assertEquals(List.of("apple", "Mango", "Zebra"), names(service.list(root.toString())));
    }

    @Test
    @DisplayName("末尾斜杠、冗余的 .. 都化简掉，回填给输入框的是规范路径")
    void normalizesPath(@TempDir Path root) throws IOException {
        Files.createDirectory(root.resolve("child"));

        DirectoryListing listing = service.list(root.toString() + "/child/..");

        assertEquals(root.toString(), listing.path());
    }

    @Test
    @DisplayName("根目录没有上一级：返回 null，界面据此禁用按钮")
    void rootHasNoParent() {
        assertNull(service.list("/").parent());
    }

    @Test
    @DisplayName("超过上限只给前 500 个，并且如实标记 truncated")
    void truncatesLongListing(@TempDir Path root) throws IOException {
        for (int i = 0; i < 501; i++) {
            Files.createDirectory(root.resolve("dir-" + i));
        }

        DirectoryListing listing = service.list(root.toString());

        assertEquals(500, listing.entries().size());
        assertTrue(listing.truncated());
    }

    @Test
    @DisplayName("不存在的目录、文件、相对路径、非法路径一律拒绝，不给一个骗人的空列表")
    void rejectsBadPaths(@TempDir Path root) throws IOException {
        Path file = Files.writeString(root.resolve("pom.xml"), "<project/>");

        assertThrows(DomainException.class, () -> service.list("/tmp/regent-not-exists-9f3a"));
        assertThrows(DomainException.class, () -> service.list(file.toString()));
        assertThrows(DomainException.class, () -> service.list("relative/dir"));
        assertThrows(DomainException.class, () -> service.list("\u0000nul"));
    }
}
