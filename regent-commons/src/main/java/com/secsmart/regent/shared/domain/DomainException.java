package com.secsmart.regent.shared.domain;

/**
 * 领域异常：表示业务规则被违反，而不是技术故障。
 *
 * <p>刻意继承 {@link RuntimeException}。领域不变量失败后，调用方几乎总是要把它转换成
 * 「任务失败」或「决策挂起」这类业务结果，而不是逐层往上抛检查型异常污染方法签名。
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
