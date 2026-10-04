package com.example.cloans.domain;

import com.example.cloans.domain.enums.LoanStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 原件借阅单。
 *
 * 并发互斥不在本行内用唯一列实现，而是由 import.sql 建立的部分唯一索引保证：
 *   CREATE UNIQUE INDEX ux_active_loan ON contract_loan(contract_id)
 *   WHERE status IN ('APPROVED','PICKED_UP','OVERDUE')
 * 即“一份原件不能同时借给两人”，含待交接的批准单。
 */
@Entity
@Table(name = "contract_loan")
public class Loan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    public Contract contract;

    /** 借阅人 */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    public User borrower;

    /** 借阅用途 */
    @Column(nullable = false, length = 500)
    public String purpose;

    /** 申请借阅天数 */
    @Column(nullable = false)
    public int requestedDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public LoanStatus status = LoanStatus.PENDING;

    @Column(nullable = false)
    public LocalDateTime requestedAt = LocalDateTime.now();

    /** 审批人 */
    @ManyToOne(fetch = FetchType.EAGER)
    public User decidedBy;
    public LocalDateTime decidedAt;
    @Column(length = 300)
    public String rejectReason;

    /** 实际经手档案员（交接时记录） */
    @ManyToOne(fetch = FetchType.EAGER)
    public User archivist;
    public LocalDateTime handoverAt;

    /** 到期日（批准时确定，与借阅人、经手人同屏展示） */
    public LocalDate dueDate;
    public LocalDateTime pickedAt;

    /** 归还核验 */
    @ManyToOne(fetch = FetchType.EAGER)
    public User returnedBy;       // 办理归还的档案员
    public LocalDateTime returnedAt;
    @Column(length = 300)
    public String returnNote;

    /** 续借次数 */
    @Column(nullable = false)
    public int renewalCount = 0;

    /** 是否存在待处理的权限重检（调岗/密级变化） */
    @Column(nullable = false)
    public boolean recheckPending = false;

    /** 是否存在未闭环的归还异常（存在时不得签为已还） */
    @Column(nullable = false)
    public boolean anomalyOpen = false;

    @Version
    public Long version;
}
