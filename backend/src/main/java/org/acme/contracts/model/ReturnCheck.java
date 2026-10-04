package org.acme.contracts.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * 归还核验：档案员逐页核对附件与盖章页。
 * result 非 PASS 时借阅单进入 ANOMALY，禁止签为已还。
 */
@Entity
@Table(name = "return_check")
public class ReturnCheck extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Loan loan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Employee archivist;

    /** 实到页数 */
    @Column(nullable = false)
    public int pagesPresent;

    /** 缺失页码，逗号分隔，例如 "3,4" */
    public String missingPages = "";

    /** 缺失/异常附件描述 */
    public String missingAttachments = "";

    /** 盖章页是否齐全 */
    @Column(nullable = false)
    public boolean stampedPagesOk;

    /** 损坏描述 */
    public String damageDesc = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Enums.CheckResult result;

    public Instant createdAt = Instant.now();

    public String note;
}
