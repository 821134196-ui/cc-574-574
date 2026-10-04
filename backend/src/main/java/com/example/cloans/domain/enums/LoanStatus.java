package com.example.cloans.domain.enums;

/**
 * 借阅单状态：
 * PENDING 待审批 -> APPROVED 已批准待交接 -> PICKED_UP 已取走（在借阅人手中）
 *   -> OVERDUE 逾期未还 -> RETURNED 已归还
 * 审批拒绝 REJECTED；员工撤回 CANCELLED；权限重检不通过 REVOKED（中止借阅、催还中，仍持有原件）
 */
public enum LoanStatus {
    PENDING("待审批"),
    APPROVED("已批准待交接"),
    PICKED_UP("借阅中"),
    OVERDUE("逾期未还"),
    REVOKED("权限中止·催还中"),
    RETURNED("已归还"),
    REJECTED("已拒绝"),
    CANCELLED("已撤回");

    public final String label;
    LoanStatus(String label) { this.label = label; }

    /** 借阅仍“在册”：合同原件不在库房、且归属某位借阅人（含权限中止催还中） */
    public boolean holdsOriginal() {
        return this == APPROVED || this == PICKED_UP || this == OVERDUE || this == REVOKED;
    }

    /** 员工仍有未结清的借阅义务 */
    public boolean isActive() {
        return this == PENDING || holdsOriginal();
    }
}
