package com.pydlove.regent.execution.interfaces.dto;

import java.util.List;

/** 把文件库中的文档绑定到任务。 */
public record LibraryDocumentBindRequest(List<Long> documentIds) {
}
