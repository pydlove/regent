package com.pydlove.regent.bug.domain.repository;

import com.pydlove.regent.bug.domain.model.BugAttachment;
import com.pydlove.regent.bug.domain.model.BugAttachmentId;
import com.pydlove.regent.bug.domain.model.BugId;
import com.pydlove.regent.identity.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/** Bug 图片附件仓储。 */
public interface BugAttachmentRepository {

    BugAttachment save(BugAttachment attachment);

    Optional<BugAttachment> findById(BugAttachmentId id);

    default Optional<BugAttachment> findByIdOwned(BugAttachmentId id, UserId ownerId) {
        return findById(id);
    }

    /** 某个 Bug 的全部附件，按上传顺序（ID 升序）。 */
    List<BugAttachment> findByBug(BugId bugId);
}
