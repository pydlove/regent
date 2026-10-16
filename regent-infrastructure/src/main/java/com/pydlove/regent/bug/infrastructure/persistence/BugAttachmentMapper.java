package com.pydlove.regent.bug.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** Bug 图片附件 MyBatis 映射。SQL 在 {@code mapper/BugAttachmentMapper.xml}。 */
public interface BugAttachmentMapper {

    int insert(BugAttachmentData data);

    BugAttachmentData selectById(@Param("id") long id);

    List<BugAttachmentData> selectByBug(@Param("bugId") long bugId);
}
