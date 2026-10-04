package org.acme.contracts.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/** 合同原件：盒位、总页数、盖章页、扫描件文件名与密级。 */
@Entity
@Table(name = "contract")
public class Contract extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(unique = true, nullable = false)
    public String code;

    @Column(nullable = false)
    public String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Enums.Secrecy secrecy = Enums.Secrecy.INTERNAL;

    /** 档案盒位，例如 A-12-03 */
    public String boxLocation;

    /** 原件总页数，归还逐页核对的基准 */
    @Column(nullable = false)
    public int pages;

    /** 盖章页页码，逗号分隔，例如 "1,8" */
    public String stampedPages = "";

    /** 本地扫描目录中的文件名；null 表示未上传 */
    public String scanFile;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();
}
