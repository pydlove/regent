package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.LibraryDocument;
import com.pydlove.regent.execution.domain.model.LibraryDocumentId;
import com.pydlove.regent.execution.domain.model.LibraryType;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.List;
import java.util.Optional;

/** 项目文件库仓储。 */
public interface LibraryDocumentRepository {

    LibraryDocument save(LibraryDocument document);

    Optional<LibraryDocument> findById(LibraryDocumentId id);

    Optional<LibraryDocument> findByIdOwned(LibraryDocumentId id, UserId ownerId);

    Optional<LibraryDocument> findByProjectAndHash(ProjectId projectId, String contentHash);

    Optional<LibraryDocument> findByProjectAndTypeAndHash(ProjectId projectId,
                                                          LibraryType libraryType,
                                                          String contentHash);

    List<LibraryDocument> findByProject(ProjectId projectId);

    List<LibraryDocument> findByProjectAndType(ProjectId projectId, LibraryType libraryType);
}
