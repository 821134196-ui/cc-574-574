package org.acme.contracts.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/** 异常处理：缺页/损坏登记后 OPEN，管理员处理完成才 RESOLVED 并允许归还闭环。 */
@Entity
@Table(name = "anomaly")
public class Anomaly extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Loan loan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public ReturnCheck returnCheck;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Enums.CheckResult kind;

    @Column(nullable = false)
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Enums.AnomalyStatus status = Enums.AnomalyStatus.OPEN;

    public Instant openedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    public Employee resolvedBy;
    public Instant resolvedAt;
    public String resolution;
}
