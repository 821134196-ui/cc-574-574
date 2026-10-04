package com.example.cloans.domain;

import com.example.cloans.domain.enums.SecurityLevel;
import jakarta.persistence.*;

/** 合同（纸质原件档案） */
@Entity
@Table(name = "contracts")
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 合同编号 */
    @Column(nullable = false, unique = true)
    public String code;

    @Column(nullable = false)
    public String title;

    /** 相对方 */
    @Column(nullable = false)
    public String counterparty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public SecurityLevel securityLevel = SecurityLevel.INTERNAL;

    /** 库房盒位，如 A-3-12 */
    @Column(nullable = false)
    public String boxLocation;

    /** 原件总页数（归还逐页核对基准） */
    @Column(nullable = false)
    public int totalPages;

    /** 盖章页页码，逗号分隔，如 "1,8" */
    @Column(nullable = false)
    public String sealPages = "";

    /** 扫描件相对 app.scans.dir 的文件名，null 表示未上传 */
    public String scanFile;

    /** 被授权查看扫描件的唯一岗位（岗位授权） */
    @ManyToOne(fetch = FetchType.EAGER)
    public Post scanGrantedPost;

    @Version
    public Long version;
}
