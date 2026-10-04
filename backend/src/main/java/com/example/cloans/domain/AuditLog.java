package com.example.cloans.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 顺序审计日志：申请、审批、交接、续借、核验、异常处理、权限重检的每一步
 * 都追加一行。id 自增即全局顺序，不允许修改/删除。
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public LocalDateTime ts = LocalDateTime.now();

    @Column(nullable = false)
    public String actorName;

    @Column(nullable = false)
    public String action;

    @Column(nullable = false)
    public String entityType;

    public Long entityId;

    /** 状态迁移：PENDING -> APPROVED 等 */
    public String stateChange;

    @Column(length = 1000)
    public String detail;

    public AuditLog() {}
    public AuditLog(String actorName, String action, String entityType, Long entityId, String stateChange, String detail) {
        this.actorName = actorName;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.stateChange = stateChange;
        this.detail = detail;
    }
}
