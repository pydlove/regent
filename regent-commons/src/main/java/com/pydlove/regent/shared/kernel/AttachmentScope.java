package com.pydlove.regent.shared.kernel;

/** 图片附件所属的业务对象类型，用来隔离不同上下文落在磁盘上的目录。 */
public enum AttachmentScope {
    TASK,
    TASK_DOCUMENT,
    FILE_LIBRARY,
    BUG,
    INITIATIVE,
    REQUIREMENT_REVIEW,
    USER_AVATAR
}
