package com.pydlove.regent.bug.application.command;

import com.pydlove.regent.bug.domain.model.Bug;
import com.pydlove.regent.bug.domain.model.BugAttachment;
import com.pydlove.regent.bug.domain.model.BugId;
import com.pydlove.regent.bug.domain.model.BugSeverity;
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

/** Bug 图片附件的上传策略与归属校验。 */
class BugAttachmentServiceTest {

    private static final long MAX_BYTES = 1024L;
    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01
    };

    private final ExecutionFakes.Bugs bugs = new ExecutionFakes.Bugs();
    private final ExecutionFakes.BugAttachments attachments = new ExecutionFakes.BugAttachments();
    private final ExecutionFakes.AttachmentFiles files = new ExecutionFakes.AttachmentFiles();
    private final BugAttachmentService service =
            new BugAttachmentService(bugs, attachments, files, MAX_BYTES);

    private BugId bug() {
        Bug bug = Bug.reportManually(
                ProjectId.of(1L), "界面错位", BugSeverity.MINOR, "打开列表", "src/App.vue", null);
        bugs.save(bug);
        return bug.getId();
    }

    @Test
    @DisplayName("上传：原名保留、字节落盘、能按 Bug 列表读回")
    void storesImage() {
        BugId bugId = bug();

        BugAttachment saved = service.attach(bugId,
                new UploadedBugAttachment("复现截图.png", "IMAGE/PNG; charset=binary", PNG));

        assertEquals("复现截图.png", saved.filename());
        assertEquals("image/png", saved.contentType());
        assertTrue(saved.storagePath().contains("/bug-1/"));
        assertArrayEquals(PNG, files.stored.get(saved.storagePath()));
        assertEquals(List.of(saved), service.list(bugId));
    }

    @Test
    @DisplayName("非白名单和假图片在落盘前拒绝")
    void rejectsInvalidImage() {
        BugId bugId = bug();

        assertThrows(DomainException.class,
                () -> service.attach(bugId, new UploadedBugAttachment("x.svg", "image/svg+xml", PNG)));
        assertThrows(DomainException.class,
                () -> service.attach(bugId, new UploadedBugAttachment("x.png", "image/png",
                        "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))));
        assertTrue(files.stored.isEmpty());
    }

    @Test
    @DisplayName("Bug 不存在时拒绝上传和列表")
    void rejectsUnknownBug() {
        Bug bug = Bug.reportManually(
                ProjectId.of(1L), "还没落库", BugSeverity.MINOR, null, null, null);
        bug.assignIdentity(BugId.of(999L));

        assertThrows(EntityNotFoundException.class,
                () -> service.attach(bug.getId(), new UploadedBugAttachment("x.png", "image/png", PNG)));
        assertThrows(EntityNotFoundException.class, () -> service.list(bug.getId()));
        assertTrue(files.stored.isEmpty());
    }

    @Test
    @DisplayName("文件丢失时读取明确失败")
    void readsContentBack() {
        BugId bugId = bug();
        BugAttachment saved = service.attach(
                bugId, new UploadedBugAttachment("x.png", "image/png", PNG));

        assertArrayEquals(PNG, service.content(saved));
        files.stored.clear();
        assertThrows(EntityNotFoundException.class, () -> service.content(saved));
    }
}
