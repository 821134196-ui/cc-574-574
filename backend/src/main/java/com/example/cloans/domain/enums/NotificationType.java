package com.example.cloans.domain.enums;

/** 提醒类型（本地模拟提醒中心） */
public enum NotificationType {
    DUE_SOON("即将到期"),
    OVERDUE("逾期跟进"),
    RECHECK("权限重检"),
    ANOMALY("归还异常"),
    GATE("门禁");

    public final String label;
    NotificationType(String label) { this.label = label; }
}
