package com.example.cloans.domain;

import com.example.cloans.domain.enums.RenewalStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 续借申请：必须在到期日前提出 */
@Entity
@Table(name = "renewal_request")
public class RenewalRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    public Loan loan;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    public User requestedBy;

    @Column(nullable = false)
    public LocalDateTime requestedAt = LocalDateTime.now();

    @Column(nullable = false)
    public LocalDate oldDueDate;

    /** 申请续到的日期 */
    @Column(nullable = false)
    public LocalDate requestedDueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public RenewalStatus status = RenewalStatus.PENDING;

    @ManyToOne(fetch = FetchType.EAGER)
    public User decidedBy;
    public LocalDateTime decidedAt;
    @Column(length = 300)
    public String note;
}
