package com.example.cloans.domain;

import com.example.cloans.domain.enums.NotificationType;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 本地模拟提醒中心（代替短信/邮件/IM 网关） */
@Entity
@Table(name = "notification")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 接收人；null 表示广播给管理人员 */
    @ManyToOne(fetch = FetchType.EAGER)
    public User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public NotificationType type;

    @Column(nullable = false)
    public String title;

    @Column(nullable = false, length = 500)
    public String message;

    @Column(nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    public boolean read = false;

    public Long refLoanId;

    public Notification() {}
    public Notification(User user, NotificationType type, String title, String message, Long refLoanId) {
        this.user = user; this.type = type; this.title = title; this.message = message; this.refLoanId = refLoanId;
    }
}
