package org.acme.contracts.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** 续借申请。必须在到期前提交；逾期借阅单不允许续借。 */
@Entity
@Table(name = "renewal")
public class Renewal extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Loan loan;

    public Instant requestedAt = Instant.now();

    public LocalDate oldDueDate;
    public LocalDate newDueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Enums.RenewalStatus status = Enums.RenewalStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    public Employee decidedBy;
    public Instant decidedAt;
    public String reason;
    public String rejectReason;
}
