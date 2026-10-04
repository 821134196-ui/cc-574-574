package com.example.cloans.service;

import com.example.cloans.domain.*;
import com.example.cloans.domain.enums.*;
import com.example.cloans.web.dto.Requests;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class LoanService {

    @Inject EntityManager em;
    @Inject AuditService audit;
    @Inject NotificationService notify;
    @Inject DoorGateService door;

    @ConfigProperty(name = "app.loan.max-days") int maxDays;
    @ConfigProperty(name = "app.loan.due-soon-days") int dueSoonDays;

    /* ---------------- 申请 ---------------- */

    @Transactional
    public Loan apply(User borrower, Requests.ApplyRequest req) {
        if (req.contractId() == null) throw ApiException.badRequest("缺少合同");
        Contract c = em.find(Contract.class, req.contractId());
        if (c == null) throw ApiException.notFound("合同不存在");
        String purpose = req.purpose() == null ? "" : req.purpose().trim();
        if (purpose.length() < 5) throw ApiException.badRequest("请填写具体的借阅用途（至少 5 个字）");
        int days = req.days() == null ? 0 : req.days();
        if (days < 1 || days > maxDays) throw ApiException.badRequest("借阅天数需在 1~" + maxDays + " 天之间");

        // 密级权限：岗位许可必须达到合同密级
        if (borrower.post.clearance < c.securityLevel.level) {
            throw ApiException.forbidden(String.format(
                    "权限不足：岗位「%s」许可密级(%d)低于合同密级「%s」(%d)",
                    borrower.post.name, borrower.post.clearance, c.securityLevel.label, c.securityLevel.level));
        }

        // 本人对该合同已有未结清借阅
        Long mine = em.createQuery("select count(l) from Loan l where l.contract=:c and l.borrower=:u " +
                        "and l.status in (com.example.cloans.domain.enums.LoanStatus.PENDING," +
                        "com.example.cloans.domain.enums.LoanStatus.APPROVED," +
                        "com.example.cloans.domain.enums.LoanStatus.PICKED_UP," +
                        "com.example.cloans.domain.enums.LoanStatus.OVERDUE," +
                        "com.example.cloans.domain.enums.LoanStatus.REVOKED)", Long.class)
                .setParameter("c", c).setParameter("u", borrower).getSingleResult();
        if (mine > 0) throw ApiException.conflict("您对该合同已有未结清的借阅/申请");

        // PENDING 不持有原件，可排队；但提示当前持有人
        User holder = currentHolder(c);

        Loan l = new Loan();
        l.contract = c;
        l.borrower = borrower;
        l.purpose = purpose;
        l.requestedDays = days;
        l.status = LoanStatus.PENDING;
        em.persist(l);
        em.flush();
        audit.record(borrower.name, "申请借阅", "Loan", l.id, null,
                String.format("合同《%s》(%s)，用途：%s，申请 %d 天%s",
                        c.title, c.code, purpose, days,
                        holder == null ? "" : "；当前原件由 " + holder.name + " 持有，进入排队"));
        return l;
    }

    /* ---------------- 审批 ---------------- */

    @Transactional
    public Loan approve(User admin, Long loanId) {
        requireAdmin(admin);
        Loan l = mustLoan(loanId);
        if (l.status != LoanStatus.PENDING)
            throw ApiException.conflict("当前状态「" + l.status.label + "」不可审批");

        // 管理员不能批准自己的借阅
        if (l.borrower.id.equals(admin.id))
            throw ApiException.forbidden("管理员不能批准本人的借阅申请，请由其他管理人员审批");

        // 审批时再次校验密级（等待期间可能发生调岗/密级调整）
        if (l.borrower.post.clearance < l.contract.securityLevel.level)
            throw ApiException.forbidden("借阅人岗位密级已不满足合同要求，无法批准");

        // 一份原件不能同时借给两人：审批落库即建立互斥（部分唯一索引兜底并发）
        User holder = currentHolder(l.contract);
        if (holder != null)
            throw ApiException.conflict("原件当前由「" + holder.name + "」持有（借阅中/待交接），不能重复借出");

        LocalDate today = LocalDate.now();
        l.status = LoanStatus.APPROVED;
        l.decidedBy = admin;
        l.decidedAt = LocalDateTime.now();
        l.dueDate = today.plusDays(l.requestedDays);
        try {
            em.flush(); // 触发 ux_active_loan 部分唯一索引
        } catch (PersistenceException e) {
            String msg = String.valueOf(e.getMessage()).toLowerCase();
            if (msg.contains("ux_active_loan") || msg.contains("unique") || msg.contains("constraint")) {
                throw ApiException.conflict("并发冲突：该原件已被另一笔借阅抢先占用（一份原件不能同时借给两人）");
            }
            throw e;
        }
        audit.record(admin.name, "审批通过", "Loan", l.id, "PENDING->APPROVED",
                "应归还日期 " + l.dueDate + "，待档案员交接");
        notify.notify(l.borrower, NotificationType.GATE, "借阅已批准",
                String.format("您借阅《%s》的申请已批准，应归还日 %s，请到档案室由档案员交接原件。",
                        l.contract.title, l.dueDate), l.id);
        return l;
    }

    @Transactional
    public Loan reject(User admin, Long loanId, String reason) {
        requireAdmin(admin);
        Loan l = mustLoan(loanId);
        if (l.status != LoanStatus.PENDING)
            throw ApiException.conflict("当前状态不可拒绝");
        l.status = LoanStatus.REJECTED;
        l.decidedBy = admin;
        l.decidedAt = LocalDateTime.now();
        l.rejectReason = reason == null ? "" : reason.trim();
        audit.record(admin.name, "审批拒绝", "Loan", l.id, "PENDING->REJECTED", "原因：" + l.rejectReason);
        return l;
    }

    @Transactional
    public Loan cancel(User borrower, Long loanId) {
        Loan l = mustLoan(loanId);
        if (!l.borrower.id.equals(borrower.id))
            throw ApiException.forbidden("只能撤回本人的申请");
        if (l.status != LoanStatus.PENDING)
            throw ApiException.conflict("仅待审批的申请可撤回");
        l.status = LoanStatus.CANCELLED;
        audit.record(borrower.name, "撤回申请", "Loan", l.id, "PENDING->CANCELLED", null);
        return l;
    }

    /* ---------------- 交接（档案员取件出库） ---------------- */

    @Transactional
    public Loan handover(User archivist, Long loanId) {
        if (archivist.role != Role.ARCHIVIST && archivist.role != Role.ADMIN)
            throw ApiException.forbidden("仅法务档案员可登记交接");
        Loan l = mustLoan(loanId);
        if (l.status != LoanStatus.APPROVED)
            throw ApiException.conflict("仅「已批准待交接」的单据可交接，当前为「" + l.status.label + "」");
        if (l.recheckPending)
            throw ApiException.conflict("存在待处理的权限重检，暂缓交接");
        if (l.borrower.post.clearance < l.contract.securityLevel.level)
            throw ApiException.forbidden("交接前复核未通过：借阅人密级不足");

        l.status = LoanStatus.PICKED_UP;
        l.archivist = archivist;
        l.handoverAt = LocalDateTime.now();
        l.pickedAt = LocalDateTime.now();
        // 门禁本地模拟：出库放行
        door.issueExitPass(l);
        audit.record(archivist.name, "原件交接出库", "Loan", l.id, "APPROVED->PICKED_UP",
                String.format("从盒位 %s 取出《%s》原件，实际经手档案员：%s；借阅人：%s；到期日：%s",
                        l.contract.boxLocation, l.contract.title, archivist.name, l.borrower.name, l.dueDate));
        return l;
    }

    /* ---------------- 续借（必须到期前） ---------------- */

    @Transactional
    public RenewalRequest requestRenewal(User borrower, Long loanId, Integer days) {
        Loan l = mustLoan(loanId);
        if (!l.borrower.id.equals(borrower.id))
            throw ApiException.forbidden("只能为本人的借阅申请续借");
        if (l.status != LoanStatus.PICKED_UP)
            throw ApiException.conflict("仅借阅中的单据可申请续借，当前为「" + l.status.label + "」");
        LocalDate today = LocalDate.now();
        if (!today.isBefore(l.dueDate))
            throw ApiException.badRequest("借阅已到期（到期日 " + l.dueDate + "），不能续借；逾期单据请联系管理人员跟进");
        int d = days == null ? 0 : days;
        if (d < 1 || d > maxDays) throw ApiException.badRequest("续借天数需在 1~" + maxDays + " 天之间");
        if (l.anomalyOpen) throw ApiException.conflict("归还异常未闭环，不能续借");
        if (l.recheckPending) throw ApiException.conflict("权限重检未完成，不能续借");
        Long pending = em.createQuery("select count(r) from RenewalRequest r where r.loan=:l and " +
                "r.status=com.example.cloans.domain.enums.RenewalStatus.PENDING", Long.class)
                .setParameter("l", l).getSingleResult();
        if (pending > 0) throw ApiException.conflict("已有待审批的续借申请");

        RenewalRequest r = new RenewalRequest();
        r.loan = l;
        r.requestedBy = borrower;
        r.oldDueDate = l.dueDate;
        r.requestedDueDate = l.dueDate.plusDays(d);
        em.persist(r);
        audit.record(borrower.name, "申请续借", "RenewalRequest", r.id, null,
                "到期日 " + r.oldDueDate + " 申请续至 " + r.requestedDueDate);
        return r;
    }

    @Transactional
    public RenewalRequest decideRenewal(User admin, Long renewalId, boolean approve, String note) {
        requireAdmin(admin);
        RenewalRequest r = em.find(RenewalRequest.class, renewalId);
        if (r == null) throw ApiException.notFound("续借申请不存在");
        if (r.status != RenewalStatus.PENDING) throw ApiException.conflict("该续借申请已处理");
        Loan l = r.loan;
        if (l.borrower.id.equals(admin.id))
            throw ApiException.forbidden("管理员不能批准本人的续借申请");
        if (approve) {
            if (l.status != LoanStatus.PICKED_UP) throw ApiException.conflict("单据已不在借阅中");
            if (!LocalDate.now().isBefore(l.dueDate))
                throw ApiException.badRequest("审批时该借阅已到期，不能批准续借");
            r.status = RenewalStatus.APPROVED;
            r.decidedBy = admin;
            r.decidedAt = LocalDateTime.now();
            r.note = note;
            l.dueDate = r.requestedDueDate;
            l.renewalCount++;
            audit.record(admin.name, "批准续借", "RenewalRequest", r.id,
                    r.oldDueDate + "->" + r.requestedDueDate, "借阅单#" + l.id);
            notify.notify(l.borrower, NotificationType.DUE_SOON, "续借已批准",
                    "《" + l.contract.title + "》新的应归还日为 " + l.dueDate, l.id);
        } else {
            r.status = RenewalStatus.REJECTED;
            r.decidedBy = admin;
            r.decidedAt = LocalDateTime.now();
            r.note = note;
            audit.record(admin.name, "拒绝续借", "RenewalRequest", r.id, null, note);
        }
        return r;
    }

    /* ---------------- 逾期/临期扫描（定时 + 手动触发） ---------------- */

    @Transactional
    public int overdueSweep() {
        LocalDate today = LocalDate.now();
        int n = 0;
        List<Loan> loans = em.createQuery("select l from Loan l where l.status=:s", Loan.class)
                .setParameter("s", LoanStatus.PICKED_UP).getResultList();
        List<User> admins = em.createQuery("select u from User u where u.role=:r", User.class)
                .setParameter("r", Role.ADMIN).getResultList();
        for (Loan l : loans) {
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, l.dueDate);
            if (daysLeft < 0) {
                l.status = LoanStatus.OVERDUE;
                String msg = String.format("借阅单#%d《%s》已于 %s 到期未还，请立即跟进借阅人 %s",
                        l.id, l.contract.title, l.dueDate, l.borrower.name);
                notify.notify(l.borrower, NotificationType.OVERDUE, "借阅逾期", msg, l.id);
                for (User a : admins) notify.notify(a, NotificationType.OVERDUE, "逾期跟进提醒", msg, l.id);
                audit.record("系统", "逾期标记", "Loan", l.id, "PICKED_UP->OVERDUE", msg);
                n++;
            } else if (daysLeft <= dueSoonDays
                    && !notify.exists(NotificationType.DUE_SOON, l.id, l.borrower.id)) {
                notify.notify(l.borrower, NotificationType.DUE_SOON, "借阅即将到期",
                        String.format("《%s》将于 %s 到期，请按时归还。", l.contract.title, l.dueDate), l.id);
            }
        }
        return n;
    }

    @Transactional
    public void remind(Loan l, User admin, String message) {
        notify.notify(l.borrower, NotificationType.OVERDUE, "管理人员催还",
                (message == null || message.isBlank()
                        ? "您的借阅《" + l.contract.title + "》已逾期，请立即归还。" : message)
                        + "（借阅单#" + l.id + "）", l.id);
        audit.record(admin.name, "催还提醒", "Loan", l.id, null, message);
    }

    /* ---------------- 查询辅助 ---------------- */

    public User currentHolder(Contract c) {
        List<Loan> ls = em.createQuery(
                "select l from Loan l where l.contract=:c and l.status in " +
                        "(com.example.cloans.domain.enums.LoanStatus.APPROVED," +
                        "com.example.cloans.domain.enums.LoanStatus.PICKED_UP," +
                        "com.example.cloans.domain.enums.LoanStatus.OVERDUE," +
                        "com.example.cloans.domain.enums.LoanStatus.REVOKED)", Loan.class)
                .setParameter("c", c).setMaxResults(1).getResultList();
        return ls.isEmpty() ? null : ls.get(0).borrower;
    }

    public Loan mustLoan(Long id) {
        Loan l = em.find(Loan.class, id);
        if (l == null) throw ApiException.notFound("借阅单不存在");
        return l;
    }

    private void requireAdmin(User u) {
        if (u.role != Role.ADMIN) throw ApiException.forbidden("需要管理人员权限");
    }
}
