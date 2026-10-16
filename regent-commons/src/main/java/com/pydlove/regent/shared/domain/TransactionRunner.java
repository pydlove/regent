package com.pydlove.regent.shared.domain;

import java.util.function.Supplier;

/**
 * 事务边界端口 —— 让应用服务能声明「这一段要么全成、要么全不成」，而不用引入任何框架。
 *
 * <p>为什么不是直接写 {@code @Transactional}：本项目的硬约束是 `regent-core` 零框架依赖，
 * 而事务边界恰恰落在应用服务上。把边界抽象成一个端口，核心域那边只看见一个方法调用，
 * 实现在基础设施层交给 Spring 的 {@code TransactionTemplate}；单测里传一个直接执行的实现即可，
 * 不需要为了测一段编排去起一个数据库。
 *
 * <p>真正需要它的是跨聚合的用例：批准一条决策要同时改 {@code decision} 与 {@code task}。
 * 两次 UPDATE 之间进程挂掉，留下「决策已批准、任务还阻塞着」的记录 —— 这类不一致没有
 * 任何自动修复路径，只能人工进库改，而它又刚好处在审计链上。
 */
public interface TransactionRunner {

    /** 在事务中执行并返回结果。实现必须保证异常时回滚，并把原异常继续抛出。 */
    <T> T inTransaction(Supplier<T> action);

    default void inTransaction(Runnable action) {
        inTransaction(() -> {
            action.run();
            return null;
        });
    }
}
