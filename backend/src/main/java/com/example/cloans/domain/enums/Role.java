package com.example.cloans.domain.enums;

/** 系统角色 */
public enum Role {
    EMPLOYEE("普通员工"),
    ARCHIVIST("法务档案员"),
    ADMIN("管理人员");

    public final String label;
    Role(String label) { this.label = label; }
}
