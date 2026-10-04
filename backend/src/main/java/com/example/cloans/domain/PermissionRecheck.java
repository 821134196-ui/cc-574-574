package com.example.cloans.domain;

import com.example.cloans.domain.enums.RecheckReason;
import com.example.cloans.domain.enums.RecheckStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 借阅人调岗或合同密级改变后，对在册借阅的权限重新核验 */
@Entity
@Table(name = "permission_recheck")
public class PermissionRecheck {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    public Loan loan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public RecheckReason reason;

    @Column(nullable = false, length = 200)
    public String changeDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public RecheckStatus status = RecheckStatus.PENDING;

    @Column(nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.EAGER)
    public User checkedBy;
    public LocalDateTime checkedAt;

    @Column(length = 300)
    public String note;
}
