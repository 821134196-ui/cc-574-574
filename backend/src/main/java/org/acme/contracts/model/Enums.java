package org.acme.contracts.model;

/** 领域枚举集中定义。密级按 level 比较：数字越大越机密。 */
public final class Enums {

    private Enums() {
    }

    /** 系统角色 */
    public enum Role {
        EMPLOYEE,   // 普通员工（借阅人）
        ARCHIVIST,  // 法务档案员（出库/归还经手、逐页核验）
        ADMIN       // 管理人员（审批、异常处理、权限管理）
    }

    /** 合同密级 */
    public enum Secrecy {
        PUBLIC(1, "公开"),
        INTERNAL(2, "内部"),
        CONFIDENTIAL(3, "秘密"),
        SECRET(4, "机密");

        private final int level;
        private final String label;

        Secrecy(int level, String label) {
            this.level = level;
            this.label = label;
        }

        public int level() {
            return level;
        }

        public String label() {
            return label;
        }
    }

    /**
     * 借阅单状态：
     * PENDING 待审批 -> APPROVED 已审批待出库 -> ISSUED 已出库（借阅人持有）
     * -> OVERDUE 逾期（定时任务标记）-> RETURNED 已归还
     * 任一环节发现缺页/损坏进入 ANOMALY 异常，核验通过前不得签为已还。
     */
    public enum LoanStatus {
        PENDING, APPROVED, REJECTED, ISSUED, OVERDUE, ANOMALY, RETURNED
    }

    public enum RenewalStatus {
        PENDING, APPROVED, REJECTED
    }

    public enum HandoverKind {
        ISSUE, RETURN
    }

    public enum CheckResult {
        PASS, MISSING_PAGE, DAMAGED
    }

    public enum AnomalyStatus {
        OPEN, RESOLVED
    }

    public enum ReminderKind {
        DUE_SOON, OVERDUE, FOLLOWUP
    }

    public enum DoorDirection {
        OUT, IN
    }

    /** 权限重核验状态：NONE 无需重验 / REQUIRED 必须重验 / OK 已重验通过 */
    public enum ReverifyStatus {
        NONE, REQUIRED, OK
    }
}
