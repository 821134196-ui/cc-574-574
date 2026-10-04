package com.example.cloans.domain;

import com.example.cloans.domain.enums.AnomalyStatus;
import com.example.cloans.domain.enums.PageCheckStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 归还异常单：缺页/损坏。未处理完不得签为已还 */
@Entity
@Table(name = "anomaly")
public class Anomaly {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    public Loan loan;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    public Contract contract;

    /** MISSING 缺页 / DAMAGED 损坏 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public PageCheckStatus issueType;

    public Integer pageNo;

    @Column(nullable = false, length = 500)
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AnomalyStatus status = AnomalyStatus.OPEN;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    public User reportedBy;
    @Column(nullable = false)
    public LocalDateTime reportedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.EAGER)
    public User handledBy;
    public LocalDateTime handledAt;

    @Column(length = 500)
    public String resolution;
}
