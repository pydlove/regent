package com.pydlove.regent.delivery.domain.repository;

import com.pydlove.regent.delivery.domain.model.InitiativeAttachment;
import com.pydlove.regent.delivery.domain.model.InitiativeAttachmentId;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.identity.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/** 立项会议纪要附件仓储。 */
public interface InitiativeAttachmentRepository {

    InitiativeAttachment save(InitiativeAttachment attachment);

    Optional<InitiativeAttachment> findById(InitiativeAttachmentId id);

    Optional<InitiativeAttachment> findByIdOwned(InitiativeAttachmentId id, UserId ownerId);

    List<InitiativeAttachment> findByInitiative(InitiativeId initiativeId);
}
