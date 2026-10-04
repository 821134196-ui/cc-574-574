package com.example.cloans.domain.enums;

/** 权限重新核验的触发原因 */
public enum RecheckReason {
    TRANSFER("借阅人调岗"),
    LEVEL_CHANGED("合同密级调整");

    public final String label;
    RecheckReason(String label) { this.label = label; }
}
