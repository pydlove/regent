package com.pydlove.regent.shared.infrastructure.tx;

import com.pydlove.regent.shared.domain.TransactionRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Supplier;

/**
 * 事务端口的 Spring 实现。
 *
 * <p>用 {@code TransactionTemplate} 而不是在应用服务上标 {@code @Transactional}：
 * 注解要求那个类住在被 Spring 代理的容器里，而应用服务住在 {@code regent-core}，
 * 那个模块必须保持零框架依赖。模板写在这里，核心域只看见一个方法调用。
 *
 * <p>默认传播行为（REQUIRED）就是这里要的：外层已有事务时并入，没有就开一个。
 */
@Component
public class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate template;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T inTransaction(Supplier<T> action) {
        return template.execute(status -> action.get());
    }

    @Override
    public void afterCompletion(Runnable onCommit, Runnable onRollback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            onCommit.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    onCommit.run();
                } else if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    onRollback.run();
                }
            }
        });
    }
}
