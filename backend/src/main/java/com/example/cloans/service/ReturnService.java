package com.example.cloans.service;

import com.example.cloans.domain.*;
import com.example.cloans.domain.enums.*;
import com.example.cloans.web.dto.Requests;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 归还核验：档案员逐页核对（含盖章页）。
 * 任一页缺页/损坏 => 生成异常单、借阅单 anomalyOpen=true、状态保持在册，不能签为已还；
 * 异常全部闭环后重新核验，全部完好方可签还。
 */
@ApplicationScoped
public class ReturnService {

    @Inject EntityManager em;
    @Inject AuditService audit;
    @Inject NotificationService notify;
    @Inject DoorGateService door;

    @Transactional
    public Map<String, Object> check(User archivist, Requests.PageCheckRequest req) {
        if (archivist.role != Role.ARCHIVIST && archivist.role != Role.ADMIN)
            throw ApiException.forbidden("仅法务档案员可办理归还核验");
        if (req.loanId() == null) throw ApiException.badRequest("缺少借阅单");
        Loan l = em.find(Loan.class, req.loanId());
        if (l == null) throw ApiException.notFound("借阅单不存在");
        if (!l.status.holdsOriginal())
            throw ApiException.conflict("单据当前为「" + l.status.label + "」，无原件可核验");

        Contract c = l.contract;
        Set<Integer> seals = parseSeals(c.sealPages);
        List<Requests.PageResult> pages = req.pages() == null ? List.of() : req.pages();
        if (pages.size() != c.totalPages)
            throw ApiException.badRequest(String.format("需逐页核对全部 %d 页（含盖章页 %s），本次提交 %d 页",
                    c.totalPages, c.sealPages, pages.size()));

        int round = em.createQuery("select coalesce(max(p.round),0) from PageCheck p where p.loan=:l", Integer.class)
                .setParameter("l", l).getSingleResult() + 1;

        Set<Integer> seen = new HashSet<>();
        List<String> issues = new ArrayList<>();
        for (Requests.PageResult pr : pages) {
            PageCheckStatus st;
            try { st = PageCheckStatus.valueOf(pr.status() == null ? "OK" : pr.status().trim().toUpperCase()); }
            catch (IllegalArgumentException e) { throw ApiException.badRequest("未知的核验状态: " + pr.status()); }
            if (pr.pageNo() < 1 || pr.pageNo() > c.totalPages)
                throw ApiException.badRequest("页码超出范围: " + pr.pageNo());
            if (!seen.add(pr.pageNo())) throw ApiException.badRequest("页码重复: " + pr.pageNo());
            boolean isSeal = seals.contains(pr.pageNo());
            PageCheck pc = new PageCheck();
            pc.loan = l; pc.round = round; pc.pageNo = pr.pageNo(); pc.sealPage = isSeal;
            pc.status = st; pc.note = pr.note();
            em.persist(pc);
            if (st.hasIssue()) {
                issues.add((st == PageCheckStatus.MISSING ? "缺页" : "损坏") + "第" + pr.pageNo() + "页"
                        + (isSeal ? "(盖章页)" : "")
                        + (pr.note() == null || pr.note().isBlank() ? "" : "：" + pr.note()));
            }
        }
        // 盖章页完整性重点拦截
        for (int s : seals) {
            int idx = s - 1;
            Requests.PageResult pr = pages.stream().filter(p -> p.pageNo() == s).findFirst().orElse(null);
            if (pr == null) throw ApiException.badRequest("缺少盖章页第" + s + "页的核验记录");
        }

        Map<String, Object> result = D.of("round", round, "issueCount", issues.size(), "issues", issues);

        if (issues.isEmpty()) {
            if (l.anomalyOpen)
                throw ApiException.conflict("本单核验已完好，但历史异常单尚未闭环，请先完成异常处理后再签还");
            // 全部完好且无未闭环异常 -> 签为已还
            return finishReturn(archivist, l, round, "逐页核对全部完好（含盖章页 " + c.sealPages + "）", true);
        }

        // 发现缺页/损坏：进入异常处理，明确不能签为已还
        l.anomalyOpen = true;
        // 状态保持在册（逾期+异常仍为逾期），绝不转为 RETURNED
        for (String issue : issues) {
            String type = issue.startsWith("缺页") ? "MISSING" : "DAMAGED";
            Integer pageNo = extractPageNo(issue);
            Anomaly a = new Anomaly();
            a.loan = l; a.contract = c;
            a.issueType = PageCheckStatus.valueOf(type);
            a.pageNo = pageNo;
            a.description = issue;
            a.reportedBy = archivist;
            em.persist(a);
        }
        String msg = String.format("借阅单#%d《%s》归还核验发现问题：%s。原件暂缓入库，已转异常处理，不得签为已还。",
                l.id, c.title, String.join("；", issues));
        for (User u : em.createQuery("select u from User u where u.role=:r", User.class)
                .setParameter("r", Role.ADMIN).getResultList())
            notify.notify(u, NotificationType.ANOMALY, "归还异常待处理", msg, l.id);
        notify.notify(l.borrower, NotificationType.ANOMALY, "归还核验异常", msg, l.id);
        audit.record(archivist.name, "归还核验-发现异常", "Loan", l.id, null,
                "第" + round + "轮：" + String.join("；", issues) + "。已生成异常单，禁止签还");
        result.put("blockedReturn", true);
        result.put("message", "发现缺页/损坏，已进入异常处理，不能签为已还");
        return result;
    }

    /** 管理员处理异常（补回/修复/追责说明等）；所有异常闭环后允许重新核验签还 */
    @Transactional
    public Anomaly resolveAnomaly(User admin, Long anomalyId, String resolution) {
        if (admin.role != Role.ADMIN) throw ApiException.forbidden("需要管理人员权限");
        Anomaly a = em.find(Anomaly.class, anomalyId);
        if (a == null) throw ApiException.notFound("异常单不存在");
        if (a.status == AnomalyStatus.RESOLVED) throw ApiException.conflict("该异常单已处理");
        if (resolution == null || resolution.trim().length() < 4)
            throw ApiException.badRequest("请填写处理结论（补回/修复/追责等，至少 4 个字）");
        a.status = AnomalyStatus.RESOLVED;
        a.handledBy = admin;
        a.handledAt = LocalDateTime.now();
        a.resolution = resolution.trim();
        audit.record(admin.name, "处理归还异常", "Anomaly", a.id, "OPEN->RESOLVED",
                a.description + "；处理结论：" + a.resolution);

        Loan l = a.loan;
        Long open = em.createQuery("select count(x) from Anomaly x where x.loan=:l and " +
                "x.status <> :s", Long.class).setParameter("l", l).setParameter("s", AnomalyStatus.RESOLVED)
                .getSingleResult();
        if (open == 0) {
            l.anomalyOpen = false;
            audit.record("系统", "异常全部闭环", "Loan", l.id, null, "请档案员重新逐页核验，完好方可签还");
            notify.notify(l.borrower, NotificationType.ANOMALY,
                    "异常已处理，请重新核验", "您借阅单#" + l.id + " 的异常已全部处理，请携带原件到档案室重新核验。", l.id);
        }
        return a;
    }

    private Map<String, Object> finishReturn(User archivist, Loan l, int round, String note, boolean gate) {
        if (l.anomalyOpen) throw ApiException.conflict("仍有异常单未闭环，不能签为已还");
        String from = l.status.name();
        l.status = LoanStatus.RETURNED;
        l.returnedBy = archivist;
        l.returnedAt = LocalDateTime.now();
        l.returnNote = "第" + round + "轮核验：" + note;
        if (gate) door.issueReturnPass(l);
        audit.record(archivist.name, "归还签收入库", "Loan", l.id, from + "->RETURNED",
                l.returnNote + "；原件放回盒位 " + l.contract.boxLocation + "，门禁核销出库放行");
        return D.of("returned", true, "message", "逐页核对完好，已签为已还并入库",
                "round", round, "boxLocation", l.contract.boxLocation);
    }

    private Set<Integer> parseSeals(String s) {
        if (s == null || s.isBlank()) return Set.of();
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty())
                .map(Integer::parseInt).collect(Collectors.toCollection(HashSet::new));
    }

    private Integer extractPageNo(String issue) {
        int i = issue.indexOf('第');
        int j = issue.indexOf('页');
        if (i >= 0 && j > i) {
            try { return Integer.parseInt(issue.substring(i + 1, j)); } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
