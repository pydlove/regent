package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskAttachment;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 附件用例测试。
 *
 * <p>守的是「什么东西能进这条链路」：类型、大小、内容得像一张真的图片。
 * 放进来一张改过后缀的假图，代价不是报错，而是模型对着一团坏字节干活。
 */
class TaskAttachmentServiceTest {

    private static final long MAX_BYTES = 1024L;

    private final ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
    private final ExecutionFakes.Attachments attachments = new ExecutionFakes.Attachments();
    private final ExecutionFakes.AttachmentFiles files = new ExecutionFakes.AttachmentFiles();
    private final TaskAttachmentService service =
            new TaskAttachmentService(tasks, attachments, files, MAX_BYTES);

    /** 最小的合法 PNG 头，够过魔数校验。 */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};

    private TaskId task() {
        Task task = Task.manual(ProjectId.of(1L), "带图的任务", "按截图改一下样式", 0);
        // 走 save 而不是 add：替身在这里发号，和真实仓储回填主键的行为一致
        tasks.save(task);
        return task.getId();
    }

    private TaskId missingTask() {
        Task task = Task.manual(ProjectId.of(1L), "还没落库", "指令", 0);
        task.assignIdentity(TaskId.of(999L));
        return task.getId();
    }

    @Test
    @DisplayName("图片落盘并入库：原名保留、类型归一化、字节原样存下来")
    void storesImage() {
        TaskId taskId = task();

        TaskAttachment saved = service.attach(taskId,
                new UploadedAttachment("屏幕截图 2026-10-16.png", "IMAGE/PNG; charset=binary", PNG));

        assertEquals("屏幕截图 2026-10-16.png", saved.filename(), "原文件名只用于展示，可以带中文");
        assertEquals("image/png", saved.contentType());
        assertEquals(PNG.length, saved.sizeBytes());
        assertTrue(saved.storagePath().endsWith(".png"), "磁盘上只留 UUID + 扩展名：" + saved.storagePath());
        assertArrayEquals(PNG, files.stored.get(saved.storagePath()));
        assertEquals(List.of(saved), service.list(taskId));
    }

    @Test
    @DisplayName("只看最后一段文件名：路径不进展示名")
    void keepsBasenameOnly() {
        TaskId taskId = task();

        TaskAttachment saved = service.attach(taskId,
                new UploadedAttachment("C:\\Users\\me\\Desktop\\shot.png", "image/png", PNG));

        assertEquals("shot.png", saved.filename());
    }

    @Test
    @DisplayName("文件名缺失时按类型补一个默认名，不留空")
    void fallsBackToDefaultName() {
        TaskId taskId = task();

        TaskAttachment saved = service.attach(taskId, new UploadedAttachment(null, "image/png", PNG));

        assertEquals("image.png", saved.filename());
    }

    @Test
    @DisplayName("白名单外的类型拒收：SVG 能带脚本，服务端原样回吐就是 XSS 入口")
    void rejectsNonWhitelistedType() {
        TaskId taskId = task();

        assertThrows(DomainException.class,
                () -> service.attach(taskId, new UploadedAttachment("logo.svg", "image/svg+xml", PNG)));
        assertThrows(DomainException.class,
                () -> service.attach(taskId, new UploadedAttachment("report.pdf", "application/pdf", PNG)));
        assertThrows(DomainException.class,
                () -> service.attach(taskId, new UploadedAttachment("x.png", null, PNG)));
        assertTrue(files.stored.isEmpty(), "被拒的上传不该落盘");
    }

    @Test
    @DisplayName("超过上限拒绝，并把上限说出来")
    void rejectsOversizedImage() {
        TaskId taskId = task();
        byte[] tooBig = new byte[(int) MAX_BYTES + 1];
        System.arraycopy(PNG, 0, tooBig, 0, PNG.length);

        DomainException error = assertThrows(DomainException.class,
                () -> service.attach(taskId, new UploadedAttachment("big.png", "image/png", tooBig)));

        assertTrue(error.getMessage().contains(String.valueOf(MAX_BYTES)), "报错要带上限：" + error.getMessage());
        assertTrue(files.stored.isEmpty());
    }

    @Test
    @DisplayName("内容不是真图片时拒绝：改后缀骗不过魔数")
    void rejectsFakeImage() {
        TaskId taskId = task();
        byte[] notAnImage = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        assertThrows(DomainException.class,
                () -> service.attach(taskId, new UploadedAttachment("x.png", "image/png", notAnImage)));
        assertThrows(DomainException.class,
                () -> service.attach(taskId, new UploadedAttachment("x.png", "image/png", new byte[0])));
        assertTrue(files.stored.isEmpty());
    }

    @Test
    @DisplayName("任务不存在时拒绝：附件必须挂在真实任务上")
    void rejectsUnknownTask() {
        assertThrows(EntityNotFoundException.class, () -> service.attach(missingTask(),
                new UploadedAttachment("x.png", "image/png", PNG)));
        assertThrows(EntityNotFoundException.class, () -> service.list(missingTask()));
    }

    @Test
    @DisplayName("读回内容：文件没了要报「不在了」而不是给空数组")
    void readsContentBack() {
        TaskId taskId = task();
        TaskAttachment saved = service.attach(taskId, new UploadedAttachment("x.png", "image/png", PNG));

        assertArrayEquals(PNG, service.content(saved));
        files.stored.clear();
        assertThrows(EntityNotFoundException.class, () -> service.content(saved));
    }
}
