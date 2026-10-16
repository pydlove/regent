package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.execution.application.command.TaskAttachmentService;
import com.pydlove.regent.execution.application.command.UploadedAttachment;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskAttachment;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
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

/**
 * 附件的集成测试 —— 覆盖「上传 → 落盘 → 入库 → 读回」这条真接缝：
 * 文件真的写到了磁盘上、路径真的进了 task_attachment、任务外键真的拦得住。
 *
 * <p>存储目录改到临时目录：真跑到 ~/.regent/attachments 里会在开发机上留一地测试图片，
 * 而事务回滚只管数据库、不管文件系统。文件名与 TaskLifecycleIT 同一套路。
 */
@SpringBootTest(properties = "regent.attachments.dir=${java.io.tmpdir}/regent-it-attachments")
@ActiveProfiles("test")
@Transactional
class TaskAttachmentIT {

    private static final long PROJECT_ID = 9802L;

    /** 最小的合法 PNG：8 字节签名 + 两字节数据，够过魔数校验。 */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};

    @Autowired
    private TaskAttachmentService attachmentService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Path storageRoot;

    @BeforeEach
    void 准备项目与存储目录() throws IOException {
        storageRoot = Path.of(System.getProperty("java.io.tmpdir"), "regent-it-attachments");
        jdbcTemplate.update("""
                INSERT INTO project (id, code, name, path, default_decision_level, enabled, deleted, created_at, updated_at)
                VALUES (?, 'it_attachment', 'IT 附件项目', '/tmp/it-attachment', 'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE name = VALUES(name)
                """, PROJECT_ID);
    }

    @AfterEach
    void 清理落盘文件() throws IOException {
        // 只删本次用例对应的任务目录：临时目录里可能有别人（另一次运行）留下的文件
        if (!Files.isDirectory(storageRoot)) {
            return;
        }
        try (var dirs = Files.list(storageRoot)) {
            dirs.filter(path -> path.getFileName().toString().startsWith("task-"))
                    .forEach(TaskAttachmentIT::deleteRecursively);
        }
    }

    private TaskId 新建任务(String title) {
        Task task = Task.manual(ProjectId.of(PROJECT_ID), title, "按截图改样式", 0);
        taskRepository.save(task);
        return task.getId();
    }

    @Test
    @DisplayName("上传：文件真落盘、行真入库、内容能读回")
    void 上传并入库() throws IOException {
        TaskId taskId = 新建任务("带图的任务");

        TaskAttachment saved = attachmentService.attach(taskId,
                new UploadedAttachment("界面截图.png", "image/png", PNG));

        assertTrue(saved.storagePath().startsWith(storageRoot.toString()),
                "落盘位置要跟着配置走：" + saved.storagePath());
        assertTrue(Files.exists(Path.of(saved.storagePath())), "文件必须真的在磁盘上");
        assertArrayEquals(PNG, Files.readAllBytes(Path.of(saved.storagePath())));

        String pathInDb = jdbcTemplate.queryForObject(
                "SELECT storage_path FROM task_attachment WHERE id = ?", String.class, saved.id().value());
        assertEquals(saved.storagePath(), pathInDb);
        assertEquals(1, (int) jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_attachment WHERE task_id = ?", Integer.class, taskId.value()));
    }

    @Test
    @DisplayName("列表按上传顺序返回，多张图不会串任务")
    void 列表按上传顺序() {
        TaskId taskId = 新建任务("两张图的任务");
        TaskId other = 新建任务("别的任务");

        attachmentService.attach(taskId, new UploadedAttachment("a.png", "image/png", PNG));
        attachmentService.attach(other, new UploadedAttachment("b.png", "image/png", PNG));
        attachmentService.attach(taskId, new UploadedAttachment("c.png", "image/png", PNG));

        List<TaskAttachment> mine = attachmentService.list(taskId);

        assertEquals(List.of("a.png", "c.png"), mine.stream().map(TaskAttachment::filename).toList());
    }

    @Test
    @DisplayName("任务不存在时外键拦得住：上传不会留下孤儿行")
    void 拒绝不存在的任务() {
        assertThrows(EntityNotFoundException.class, () -> attachmentService.attach(TaskId.of(987_655L),
                new UploadedAttachment("x.png", "image/png", PNG)));
        assertEquals(0, (int) jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_attachment WHERE task_id = 987655", Integer.class));
    }

    @Test
    @DisplayName("非白名单类型在落库前就被拒绝")
    void 拒绝非图片() {
        TaskId taskId = 新建任务("收图的任务");

        assertThrows(DomainException.class, () -> attachmentService.attach(taskId,
                new UploadedAttachment("logo.svg", "image/svg+xml", PNG)));
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
