package com.example.cloans.domain.enums;

/** 合同密级，level 越大越机密。岗位 clearance 达到合同密级才可借阅/查看扫描件 */
public enum SecurityLevel {
    INTERNAL(1, "内部"),
    CONFIDENTIAL(2, "秘密"),
    SECRET(3, "机密");

    public final int level;
    public final String label;
    SecurityLevel(int level, String label) {
        this.level = level;
        this.label = label;
    }
}
