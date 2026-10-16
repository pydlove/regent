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

    /**
     * 注册事务完成后的提交或回滚动作；当前没有事务时执行提交动作。
     *
     * <p>异步执行器不能在事务提交前启动：工作线程会拿另一条连接读取任务和运行记录，
     * 此时调用方尚未提交的数据对它不可见。跨聚合用例把「提交后启动、回滚后清理」
     * 交给这个端口，核心域仍不需要知道 Spring 的事务同步机制。
     */
    default void afterCompletion(Runnable onCommit, Runnable onRollback) {
        onCommit.run();
    }

    default void inTransaction(Runnable action) {
        inTransaction(() -> {
            action.run();
            return null;
        });
    }
}
