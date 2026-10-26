package com.pydlove.regent.execution.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 项目文件库 MyBatis 映射。 */
public interface LibraryDocumentMapper {
    int insert(LibraryDocumentData data);
    LibraryDocumentData selectById(@Param("id") long id);
    LibraryDocumentData selectByIdOwned(@Param("id") long id, @Param("ownerId") long ownerId);
    LibraryDocumentData selectByProjectAndHash(@Param("projectId") long projectId,
                                               @Param("contentHash") String contentHash);
    LibraryDocumentData selectByProjectAndTypeAndHash(@Param("projectId") long projectId,
                                                      @Param("libraryType") String libraryType,
                                                      @Param("contentHash") String contentHash);
    List<LibraryDocumentData> selectByProject(@Param("projectId") long projectId);
    List<LibraryDocumentData> selectByProjectAndType(@Param("projectId") long projectId,
                                                     @Param("libraryType") String libraryType);
}
