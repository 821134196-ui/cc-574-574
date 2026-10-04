package org.acme.contracts.service;

import org.acme.contracts.model.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 含关联的实体转安全 Map，避免 REST 序列化懒加载关联与循环引用。 */
public final class Views {

    private Views() {
    }

    public static Map<String, Object> renewal(Renewal r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id);
        m.put("loanId", r.loan.id);
        m.put("requestedAt", r.requestedAt);
        m.put("oldDueDate", r.oldDueDate);
        m.put("newDueDate", r.newDueDate);
        m.put("status", r.status.name());
        m.put("decidedByName", r.decidedBy == null ? null : r.decidedBy.name);
        m.put("decidedAt", r.decidedAt);
        m.put("reason", r.reason);
        m.put("rejectReason", r.rejectReason);
        return m;
    }

    public static Map<String, Object> handover(Handover h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", h.id);
        m.put("loanId", h.loan.id);
        m.put("seq", h.seq);
        m.put("prevId", h.prevId);
        m.put("kind", h.kind.name());
        m.put("archivistName", h.archivist.name);
        m.put("handler", h.handler);
        m.put("createdAt", h.createdAt);
        m.put("note", h.note);
        return m;
    }

    public static Map<String, Object> check(ReturnCheck c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id);
        m.put("loanId", c.loan.id);
        m.put("archivistName", c.archivist.name);
        m.put("pagesPresent", c.pagesPresent);
        m.put("missingPages", c.missingPages);
        m.put("missingAttachments", c.missingAttachments);
        m.put("stampedPagesOk", c.stampedPagesOk);
        m.put("damageDesc", c.damageDesc);
        m.put("result", c.result.name());
        m.put("createdAt", c.createdAt);
        m.put("note", c.note);
        return m;
    }

    public static Map<String, Object> anomaly(Anomaly a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id);
        m.put("loanId", a.loan.id);
        m.put("returnCheckId", a.returnCheck == null ? null : a.returnCheck.id);
        m.put("kind", a.kind.name());
        m.put("description", a.description);
        m.put("status", a.status.name());
        m.put("openedAt", a.openedAt);
        m.put("resolvedByName", a.resolvedBy == null ? null : a.resolvedBy.name);
        m.put("resolvedAt", a.resolvedAt);
        m.put("resolution", a.resolution);
        return m;
    }

    public static Map<String, Object> door(DoorEvent d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.id);
        m.put("loanId", d.loan == null ? null : d.loan.id);
        m.put("direction", d.direction.name());
        m.put("person", d.person);
        m.put("cardNo", d.cardNo);
        m.put("granted", d.granted);
        m.put("at", d.at);
        return m;
    }

    public static Map<String, Object> reminder(Reminder r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id);
        m.put("loanId", r.loan.id);
        m.put("contractCode", r.loan.contract.code);
        m.put("borrowerName", r.loan.borrower.name);
        m.put("kind", r.kind.name());
        m.put("message", r.message);
        m.put("dueDate", r.dueDate);
        m.put("acknowledged", r.acknowledged);
        m.put("createdAt", r.createdAt);
        return m;
    }
}
