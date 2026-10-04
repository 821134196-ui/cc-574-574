package org.acme.contracts.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 借阅申请串行化锁（单实例本地系统）。
 *
 * SQLite 的部分唯一索引在事务 commit 阶段才真正加锁校验，高并发下落选事务会在提交期
 * 抛异常（HTTP 500）而非业务 409。申请流程在此进程锁内完整跑完“检查在途 -> 插入 -> 提交”，
 * 保证后来者一定能看到先到者已提交的占用单并收到明确的 409；
 * 数据库唯一索引 ux_loan_active 仍是跨连接的最终兜底。
 */
@ApplicationScoped
public class LoanCreationLock {

    private final ReentrantLock lock = new ReentrantLock(true);

    public <T> T withLock(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
