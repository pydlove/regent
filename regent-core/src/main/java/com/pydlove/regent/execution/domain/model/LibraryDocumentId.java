package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 文件库文档 ID。 */
public record LibraryDocumentId(long value) {

    public LibraryDocumentId {
        if (value <= 0) {
            throw new DomainException("文件库文档 ID 必须为正数：" + value);
        }
    }

    public static LibraryDocumentId of(long value) {
        return new LibraryDocumentId(value);
    }
}
