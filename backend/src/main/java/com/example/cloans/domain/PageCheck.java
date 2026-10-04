package com.example.cloans.domain;

import com.example.cloans.domain.enums.PageCheckStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 归还时逐页核对的一行结果（含盖章页） */
@Entity
@Table(name = "page_check")
public class PageCheck {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    public Loan loan;

    /** 第几轮核验（异常处理后可重新核验，历史轮次全部保留） */
    @Column(nullable = false)
    public int round;

    @Column(nullable = false)
    public int pageNo;

    /** 是否盖章页，盖章页必须重点核对 */
    @Column(nullable = false)
    public boolean sealPage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public PageCheckStatus status = PageCheckStatus.OK;

    @Column(length = 200)
    public String note;

    @Column(nullable = false)
    public LocalDateTime checkedAt = LocalDateTime.now();
}
