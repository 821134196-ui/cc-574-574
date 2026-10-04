package org.acme.contracts.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * 审计链：所有关键操作追加写，不修改不删除。
 * 每条带全局递增 seq 与 prevId，可从头顺序追查整个交接/核验历史。
 */
@Entity
@Table(name = "audit_log")
public class AuditLog extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 全局顺序号（代码内取 max+1，与插入同事务） */
    @Column(nullable = false, unique = true)
    public long seq;

    public Long prevId;

    @Column(nullable = false)
    public String action;

    /** 关联借阅单（可空，如合同/员工维护） */
    public Long loanId;
    public Long contractId;
    public Long employeeId;

    /** 操作人 id（系统动作为 0） */
    @Column(nullable = false)
    public Long actorId;

    @Column(nullable = false)
    public String actorName;

    /** JSON 快照 */
    public String detail;

    @Column(nullable = false)
    public Instant at = Instant.now();
}
