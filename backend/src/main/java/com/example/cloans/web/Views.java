package com.example.cloans.web;

import com.example.cloans.domain.*;
import com.example.cloans.service.D;

import java.util.*;

/** 实体 -> JSON 视图（保持字段顺序，按当前用户裁剪敏感信息） */
public final class Views {
    private Views() {}

    public static Map<String, Object> post(Post p) {
        return D.of("id", p.id, "name", p.name, "clearance", p.clearance);
    }

    public static Map<String, Object> user(User u) {
        return D.of("id", u.id, "login", u.login, "name", u.name, "role", u.role.name(),
                "roleLabel", u.role.label, "post", post(u.post), "active", u.active);
    }

    public static Map<String, Object> contract(Contract c, User viewer, Loan activeLoan, boolean scanAccessible) {
        Map<String, Object> m = D.of(
                "id", c.id, "code", c.code, "title", c.title, "counterparty", c.counterparty,
                "securityLevel", c.securityLevel.name(), "securityLevelLabel", c.securityLevel.label,
                "boxLocation", c.boxLocation, "totalPages", c.totalPages, "sealPages", c.sealPages,
                "hasScan", c.scanFile != null,
                "scanGrantedPost", c.scanGrantedPost == null ? null : post(c.scanGrantedPost),
                "scanAccessible", scanAccessible,
                "clearanceRequired", c.securityLevel.level);
        if (activeLoan != null) {
            m.put("holder", user(activeLoan.borrower));
            m.put("holderStatus", activeLoan.status.name());
            m.put("holderStatusLabel", activeLoan.status.label);
            m.put("dueDate", String.valueOf(activeLoan.dueDate));
            m.put("activeLoanId", activeLoan.id);
            m.put("anomalyOpen", activeLoan.anomalyOpen);
            m.put("recheckPending", activeLoan.recheckPending);
        } else {
            m.put("holder", null);
            m.put("inVault", true);
        }
        return m;
    }

    public static Map<String, Object> loan(Loan l) {
        return D.of(
                "id", l.id,
                "contractId", l.contract.id, "contractCode", l.contract.code, "contractTitle", l.contract.title,
                "contractSecurityLevel", l.contract.securityLevel.name(),
                "boxLocation", l.contract.boxLocation, "totalPages", l.contract.totalPages,
                "sealPages", l.contract.sealPages,
                "borrower", user(l.borrower),
                "purpose", l.purpose, "requestedDays", l.requestedDays,
                "status", l.status.name(), "statusLabel", l.status.label,
                "requestedAt", String.valueOf(l.requestedAt),
                "decidedBy", l.decidedBy == null ? null : l.decidedBy.name,
                "decidedAt", l.decidedAt == null ? null : String.valueOf(l.decidedAt),
                "rejectReason", l.rejectReason,
                "archivist", l.archivist == null ? null : user(l.archivist),
                "handoverAt", l.handoverAt == null ? null : String.valueOf(l.handoverAt),
                "dueDate", l.dueDate == null ? null : String.valueOf(l.dueDate),
                "pickedAt", l.pickedAt == null ? null : String.valueOf(l.pickedAt),
                "returnedBy", l.returnedBy == null ? null : l.returnedBy.name,
                "returnedAt", l.returnedAt == null ? null : String.valueOf(l.returnedAt),
                "returnNote", l.returnNote,
                "renewalCount", l.renewalCount,
                "recheckPending", l.recheckPending, "anomalyOpen", l.anomalyOpen,
                "overdue", l.status == com.example.cloans.domain.enums.LoanStatus.OVERDUE);
    }

    public static Map<String, Object> renewal(RenewalRequest r) {
        return D.of("id", r.id, "loanId", r.loan.id,
                "requestedBy", r.requestedBy.name, "requestedAt", String.valueOf(r.requestedAt),
                "oldDueDate", String.valueOf(r.oldDueDate), "requestedDueDate", String.valueOf(r.requestedDueDate),
                "status", r.status.name(),
                "decidedBy", r.decidedBy == null ? null : r.decidedBy.name, "note", r.note);
    }

    public static Map<String, Object> pageCheck(PageCheck p) {
        return D.of("id", p.id, "loanId", p.loan.id, "round", p.round, "pageNo", p.pageNo,
                "sealPage", p.sealPage, "status", p.status.name(), "statusLabel", p.status.label,
                "note", p.note, "checkedAt", String.valueOf(p.checkedAt));
    }

    public static Map<String, Object> anomaly(Anomaly a) {
        return D.of("id", a.id, "loanId", a.loan.id, "contractId", a.contract.id,
                "contractTitle", a.contract.title, "issueType", a.issueType.name(),
                "issueTypeLabel", a.issueType == com.example.cloans.domain.enums.PageCheckStatus.MISSING ? "缺页" : "损坏",
                "pageNo", a.pageNo, "description", a.description,
                "status", a.status.name(), "statusLabel", a.status.label,
                "reportedBy", a.reportedBy.name, "reportedAt", String.valueOf(a.reportedAt),
                "handledBy", a.handledBy == null ? null : a.handledBy.name,
                "handledAt", a.handledAt == null ? null : String.valueOf(a.handledAt),
                "resolution", a.resolution,
                "borrower", user(a.loan.borrower));
    }

    public static Map<String, Object> recheck(PermissionRecheck r) {
        return D.of("id", r.id, "loanId", r.loan.id, "contractTitle", r.loan.contract.title,
                "borrower", user(r.loan.borrower),
                "reason", r.reason.name(),
                "reasonLabel", r.reason == com.example.cloans.domain.enums.RecheckReason.TRANSFER ? "借阅人调岗" : "合同密级调整",
                "changeDescription", r.changeDescription,
                "status", r.status.name(), "statusLabel", r.status.label,
                "createdAt", String.valueOf(r.createdAt),
                "checkedBy", r.checkedBy == null ? null : r.checkedBy.name,
                "note", r.note);
    }

    public static Map<String, Object> notification(Notification n) {
        return D.of("id", n.id,
                "userId", n.user == null ? null : n.user.id,
                "type", n.type.name(), "typeLabel", n.type.label,
                "title", n.title, "message", n.message,
                "createdAt", String.valueOf(n.createdAt), "read", n.read, "refLoanId", n.refLoanId);
    }

    public static Map<String, Object> audit(AuditLog a) {
        return D.of("id", a.id, "ts", String.valueOf(a.ts), "actor", a.actorName,
                "action", a.action, "entityType", a.entityType, "entityId", a.entityId,
                "stateChange", a.stateChange, "detail", a.detail);
    }
}
