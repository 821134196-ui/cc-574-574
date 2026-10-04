package org.acme.contracts.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/** 员工：岗位、角色、可接触密级、扫描件授权。调岗会累加 postVersion 触发重核验。 */
@Entity
@Table(name = "employee")
public class Employee extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(unique = true, nullable = false)
    public String employeeNo;

    @Column(nullable = false)
    public String name;

    /** 当前岗位，例如 采购部-专员 */
    public String post;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Enums.Role role = Enums.Role.EMPLOYEE;

    /** 可查看/借阅的最高密级 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Enums.Secrecy clearance = Enums.Secrecy.INTERNAL;

    /** 是否获授权查看合同扫描件 */
    @Column(nullable = false)
    public boolean scanAuthorized = false;

    @Column(nullable = false)
    public boolean active = true;

    /** 岗位版本：每次调岗 +1，借阅单据此判断是否需要重新核验权限 */
    @Column(nullable = false)
    public int postVersion = 0;

    @Column(nullable = false)
    public Instant updatedAt = Instant.now();
}
