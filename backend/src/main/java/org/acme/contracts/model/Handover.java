package org.acme.contracts.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * 交接记录：档案员出库/收回原件时登记。
 * 每条记录带 seq（同一借阅单内单调递增）与 prevId，形成可顺序追查的链条。
 */
@Entity
@Table(name = "handover")
public class Handover extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Loan loan;

    /** 同一借阅单内的顺序号，从 1 开始 */
    @Column(nullable = false)
    public int seq;

    /** 上一条记录 id；链条起点为 null。用于顺序追查防断链 */
    public Long prevId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Enums.HandoverKind kind;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Employee archivist;

    /** 实际经手人姓名 */
    @Column(nullable = false)
    public String handler;

    public Instant createdAt = Instant.now();

    public String note;
}
