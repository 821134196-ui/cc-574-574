package org.acme.contracts.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 借阅单。一份合同在任一时刻至多存在一张 active 借阅单
 * （由 SQLite 部分唯一索引 ux_loan_active 强制，见 SchemaInit）。
 */
@Entity
@Table(name = "loan")
public class Loan extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Contract contract;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Employee borrower;

    /** 借阅用途 */
    @Column(nullable = false)
    public String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Enums.LoanStatus status = Enums.LoanStatus.PENDING;

    /** 部分唯一索引依据：true=在途（申请/审批/出库/逾期/异常），归还或驳回后置 false */
    @Column(nullable = false)
    public boolean active = true;

    public Instant requestedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    public Employee approvedBy;
    public Instant approvedAt;
    public String rejectReason;

    /** 档案员：从盒位取出原件并出库 */
    @ManyToOne(fetch = FetchType.LAZY)
    public Employee issuedBy;
    public Instant issuedAt;

    /** 实际经手人（现场取走/送还原件的人，可与借阅人不同） */
    public String issueHandler;
    public String returnHandler;

    /** 到期日（审批时确定） */
    public LocalDate dueDate;

    public Instant returnedAt;

    /** 审批时锁定的密级与岗位版本，用于调岗/密级变化后的重新核验 */
    @Enumerated(EnumType.STRING)
    public Enums.Secrecy secrecyAtApproval;
    public Integer postVersionAtApproval;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Enums.ReverifyStatus reverify = Enums.ReverifyStatus.NONE;

    @Column(nullable = false)
    public int renewalCount = 0;
}
