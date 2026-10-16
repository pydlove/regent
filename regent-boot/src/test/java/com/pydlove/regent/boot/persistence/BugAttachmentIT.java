package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.bug.application.command.BugAttachmentService;
import com.pydlove.regent.bug.application.command.UploadedBugAttachment;
import com.pydlove.regent.bug.domain.model.Bug;
import com.pydlove.regent.bug.domain.model.BugAttachment;
import com.pydlove.regent.bug.domain.model.BugId;
import com.pydlove.regent.bug.domain.model.BugSeverity;
import com.pydlove.regent.bug.domain.repository.BugRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bug 图片附件“上传 → 落盘 → 入库 → 读回”的集成测试。 */
@SpringBootTest(properties = "regent.attachments.dir=${java.io.tmpdir}/regent-it-attachments")
@ActiveProfiles("test")
@Transactional
class BugAttachmentIT {

    private static final long PROJECT_ID = 9803L;
    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01
    };

    @Autowired
    private BugAttachmentService attachmentService;

    @Autowired
    private BugRepository bugRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Path storageRoot;

    @BeforeEach
    void 准备项目与存储目录() {
        storageRoot = Path.of(System.getProperty("java.io.tmpdir"), "regent-it-attachments");
        jdbcTemplate.update("""
                INSERT INTO project (id, code, name, path, default_decision_level, enabled, deleted, created_at, updated_at)
                VALUES (?, 'bug_attachment_it', 'Bug 附件 IT 项目', '/tmp/bug-attachment-it', 'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE name = VALUES(name)
                """, PROJECT_ID);
    }

    @AfterEach
    void 清理落盘文件() throws IOException {
        if (!Files.isDirectory(storageRoot)) {
            return;
        }
        try (var dirs = Files.list(storageRoot)) {
            dirs.filter(path -> path.getFileName().toString().startsWith("bug-"))
                    .forEach(BugAttachmentIT::deleteRecursively);
        }
    }

    private BugId 新建Bug() {
        Bug bug = Bug.reportManually(ProjectId.of(PROJECT_ID), "截图里的按钮错位",
                BugSeverity.MINOR, "打开页面即可看到", "src/App.vue", null);
        bugRepository.save(bug);
        return bug.getId();
    }

    @Test
    @DisplayName("上传后文件真落盘、行真入库、内容能读回")
    void 上传并入库() throws IOException {
        BugId bugId = 新建Bug();

        BugAttachment saved = attachmentService.attach(
                bugId, new UploadedBugAttachment("复现截图.png", "image/png", PNG));

        assertTrue(saved.storagePath().startsWith(storageRoot.toString()));
        assertTrue(Files.exists(Path.of(saved.storagePath())));
        assertArrayEquals(PNG, Files.readAllBytes(Path.of(saved.storagePath())));

        String pathInDb = jdbcTemplate.queryForObject(
                "SELECT storage_path FROM bug_attachment WHERE id = ?", String.class, saved.id().value());
        assertEquals(saved.storagePath(), pathInDb);
        assertEquals(1, (int) jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bug_attachment WHERE bug_id = ?", Integer.class, bugId.value()));
    }

    @Test
    @DisplayName("附件按上传顺序返回，多张图不会串 Bug")
    void 列表按上传顺序() {
        BugId bugId = 新建Bug();
        BugId other = 新建Bug();

        attachmentService.attach(bugId, new UploadedBugAttachment("a.png", "image/png", PNG));
        attachmentService.attach(other, new UploadedBugAttachment("b.png", "image/png", PNG));
        attachmentService.attach(bugId, new UploadedBugAttachment("c.png", "image/png", PNG));

        List<BugAttachment> mine = attachmentService.list(bugId);

        assertEquals(List.of("a.png", "c.png"), mine.stream().map(BugAttachment::filename).toList());
    }

    @Test
    @DisplayName("Bug 不存在时外键拦得住，上传不会留下孤儿行")
    void 拒绝不存在的Bug() {
        assertThrows(EntityNotFoundException.class, () -> attachmentService.attach(
                BugId.of(987_654L), new UploadedBugAttachment("x.png", "image/png", PNG)));
        assertEquals(0, (int) jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bug_attachment WHERE bug_id = 987654", Integer.class));
    }

    @Test
    @DisplayName("非白名单类型在落盘前就被拒绝")
    void 拒绝非图片() {
        BugId bugId = 新建Bug();

        assertThrows(DomainException.class, () -> attachmentService.attach(
                bugId, new UploadedBugAttachment("logo.svg", "image/svg+xml", PNG)));
    }

    private static void deleteRecursively(Path path) {
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ignored) {
                    // 临时目录清理失败不影响断言
                }
            });
        } catch (IOException ignored) {
            // 同上
        }
    }
}
