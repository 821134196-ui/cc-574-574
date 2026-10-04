package com.example.cloans.service;

import com.example.cloans.domain.*;
import com.example.cloans.domain.enums.*;
import com.example.cloans.web.dto.Requests;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限管理：
 * - 员工调岗 -> 对其在册借阅生成“调岗重检”
 * - 合同密级调整 -> 对该合同在册借阅生成“密级重检”，扫描件授权岗位同步重估
 * 重检结论：通过则解除挂起；不通过则借阅中止（REVOKED 催还中），且未交接的不允许交接。
 */
@ApplicationScoped
public class PermissionService {

    @Inject EntityManager em;
    @Inject AuditService audit;
    @Inject NotificationService notify;

    /** 员工调岗（管理员操作） */
    @Transactional
    public User transfer(User admin, Long userId, Long postId) {
        if (admin.role != Role.ADMIN) throw ApiException.forbidden("需要管理人员权限");
        User u = em.find(User.class, userId);
        if (u == null) throw ApiException.notFound("用户不存在");
        Post p = em.find(Post.class, postId);
        if (p == null) throw ApiException.notFound("岗位不存在");
        if (u.post.id.equals(p.id)) throw ApiException.badRequest("员工已在该岗位");

        String oldPost = u.post.name + "(许可" + u.post.clearance + ")";
        String newPost = p.name + "(许可" + p.clearance + ")";
        u.post = p;
        String desc = "调岗：" + oldPost + " -> " + newPost;
        audit.record(admin.name, "员工调岗", "User", u.id, null, desc);

        // 对在册借阅生成重检（排除已进入异常处理闭环流程的？仍需重检）
        List<Loan> active = activeLoansOf(u);
        for (Loan l : active) createRecheck(l, RecheckReason.TRANSFER, desc,
                !notifyRecheckPasses(l) ? "新岗位密级不足，默认中止" : "等待人工核验");
        return u;
    }

    /** 合同密级调整（管理员操作） */
    @Transactional
    public Contract changeLevel(User admin, Long contractId, SecurityLevel level, Long scanGrantedPostId) {
        if (admin.role != Role.ADMIN) throw ApiException.forbidden("需要管理人员权限");
        Contract c = em.find(Contract.class, contractId);
        if (c == null) throw ApiException.notFound("合同不存在");
        String old = c.securityLevel.label;
        if (c.securityLevel == level && scanGrantedPostId == null)
            throw ApiException.badRequest("密级未变化");
        c.securityLevel = level;
        if (scanGrantedPostId != null) {
            if (scanGrantedPostId == 0L) c.scanGrantedPost = null;
            else c.scanGrantedPost = em.find(Post.class, scanGrantedPostId);
        }
        String desc = "密级调整：" + old + " -> " + level.label
                + (scanGrantedPostId != null ? "；扫描件授权岗位已更新" : "");
        audit.record(admin.name, "合同密级调整", "Contract", c.id, old + "->" + level.name(), desc);

        for (Loan l : activeLoansOf(c)) {
            boolean stillOk = l.borrower.post.clearance >= level.level;
            createRecheck(l, RecheckReason.LEVEL_CHANGED, desc, stillOk ? "等待人工核验" : "新密级高于借阅人岗位许可，默认中止");
        }
        return c;
    }

    /** 管理人员对重检单作出结论 */
    @Transactional
    public PermissionRecheck decideRecheck(User admin, Long recheckId, boolean pass, String note) {
        if (admin.role != Role.ADMIN) throw ApiException.forbidden("需要管理人员权限");
        PermissionRecheck r = em.find(PermissionRecheck.class, recheckId);
        if (r == null) throw ApiException.notFound("重检单不存在");
        if (r.status != RecheckStatus.PENDING) throw ApiException.conflict("该重检单已处理");
        Loan l = r.loan;

        // 通过也必须与当前真实密级相符（防止处理期间再次变化）
        boolean currentlyOk = l.borrower.post.clearance >= l.contract.securityLevel.level;
        if (pass && !currentlyOk)
            throw ApiException.forbidden("当前岗位许可仍低于合同密级，不能判定通过");
        if (pass && l.anomalyOpen)
            throw ApiException.conflict("归还异常未闭环，不能恢复借阅");

        r.status = pass ? RecheckStatus.PASSED : RecheckStatus.REVOKED;
        r.checkedBy = admin;
        r.checkedAt = LocalDateTime.now();
        r.note = note;

        // 该借阅单是否还有其他待处理重检
        boolean morePending = em.createQuery("select count(x) from PermissionRecheck x where x.loan=:l and " +
                "x.status=:s", Long.class).setParameter("l", l)
                .setParameter("s", RecheckStatus.PENDING).getSingleResult() > 0;
        l.recheckPending = morePending;

        if (!pass) {
            // 权限中止：在册状态转为 REVOKED（仍占用原件，阻止再借给他人），催还
            if (l.status.holdsOriginal() && l.status != LoanStatus.REVOKED) {
                String from = l.status.name();
                l.status = LoanStatus.REVOKED;
                audit.record(admin.name, "权限中止借阅", "Loan", l.id, from + "->REVOKED",
                        "重检#" + r.id + " 不通过：" + r.changeDescription);
            }
            notify.notify(l.borrower, NotificationType.RECHECK, "借阅权限中止，请立即归还原件",
                    String.format("因「%s」，您对《%s》的借阅权限已中止，请立即归还（借阅单#%d）。",
                            r.changeDescription, l.contract.title, l.id), l.id);
        } else {
            // 若此前已被 REVOKED 且重新满足权限，恢复为借阅中（到期日不变；逾期另由扫描处理）
            if (l.status == LoanStatus.REVOKED) {
                l.status = java.time.LocalDate.now().isBefore(l.dueDate)
                        || java.time.LocalDate.now().isEqual(l.dueDate)
                        ? LoanStatus.PICKED_UP : LoanStatus.OVERDUE;
                audit.record(admin.name, "权限恢复借阅", "Loan", l.id, "REVOKED->" + l.status.name(),
                        "重检#" + r.id + " 通过");
            }
            notify.notify(l.borrower, NotificationType.RECHECK, "权限重检通过",
                    "您对《" + l.contract.title + "》的借阅权限复核通过。", l.id);
        }
        audit.record(admin.name, pass ? "权限重检通过" : "权限重检不通过",
                "PermissionRecheck", r.id, "PENDING->" + r.status.name(),
                r.changeDescription + (note == null ? "" : "；备注：" + note));
        return r;
    }

    private boolean notifyRecheckPasses(Loan l) {
        return l.borrower.post.clearance >= l.contract.securityLevel.level;
    }

    private void createRecheck(Loan l, RecheckReason reason, String desc, String hint) {
        PermissionRecheck r = new PermissionRecheck();
        r.loan = l;
        r.reason = reason;
        r.changeDescription = desc;
        em.persist(r);
        l.recheckPending = true;
        String title = reason == RecheckReason.TRANSFER ? "调岗后权限重检" : "密级变更后权限重检";
        notify.notify(l.borrower, NotificationType.RECHECK, title,
                String.format("借阅单#%d《%s》因「%s」需重新核验权限。%s", l.id, l.contract.title, desc, hint), l.id);
        audit.record("系统", title, "PermissionRecheck", null, null,
                "借阅单#" + l.id + "：" + desc + "（" + hint + "）");
    }

    private List<Loan> activeLoansOf(User u) {
        return em.createQuery("select l from Loan l where l.borrower=:u and l.status in " +
                        "(com.example.cloans.domain.enums.LoanStatus.PENDING," +
                        "com.example.cloans.domain.enums.LoanStatus.APPROVED," +
                        "com.example.cloans.domain.enums.LoanStatus.PICKED_UP," +
                        "com.example.cloans.domain.enums.LoanStatus.OVERDUE," +
                        "com.example.cloans.domain.enums.LoanStatus.REVOKED)", Loan.class)
                .setParameter("u", u).getResultList();
    }

    private List<Loan> activeLoansOf(Contract c) {
        return em.createQuery("select l from Loan l where l.contract=:c and l.status in " +
                        "(com.example.cloans.domain.enums.LoanStatus.PENDING," +
                        "com.example.cloans.domain.enums.LoanStatus.APPROVED," +
                        "com.example.cloans.domain.enums.LoanStatus.PICKED_UP," +
                        "com.example.cloans.domain.enums.LoanStatus.OVERDUE," +
                        "com.example.cloans.domain.enums.LoanStatus.REVOKED)", Loan.class)
                .setParameter("c", c).getResultList();
    }
}
