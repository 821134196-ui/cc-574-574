package com.example.cloans.web;

import com.example.cloans.domain.Loan;
import com.example.cloans.domain.PageCheck;
import com.example.cloans.domain.RenewalRequest;
import com.example.cloans.domain.enums.Role;
import com.example.cloans.service.ApiException;
import com.example.cloans.service.D;
import com.example.cloans.service.LoanService;
import com.example.cloans.web.dto.Requests;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class LoanResource {

    @Inject Identity identity;
    @Inject EntityManager em;
    @Inject LoanService loans;

    /** 借阅列表：scope=mine 仅本人（默认），all 需管理员/档案员 */
    @GET @Path("loans")
    public Object list(@QueryParam("scope") String scope,
                       @QueryParam("status") String status,
                       @QueryParam("active") Boolean active) {
        var me = identity.current();
        boolean all = "all".equals(scope);
        if (all && me.role == Role.EMPLOYEE)
            throw ApiException.forbidden("仅管理人员/档案员可查看全部借阅");
        String jpql = "select l from Loan l where 1=1";
        if (!all) jpql += " and l.borrower=:me";
        if (active != null && active)
            jpql += " and l.status in (com.example.cloans.domain.enums.LoanStatus.PENDING," +
                    "com.example.cloans.domain.enums.LoanStatus.APPROVED," +
                    "com.example.cloans.domain.enums.LoanStatus.PICKED_UP," +
                    "com.example.cloans.domain.enums.LoanStatus.OVERDUE," +
                    "com.example.cloans.domain.enums.LoanStatus.REVOKED)";
        if (status != null && !status.isBlank())
            jpql += " and l.status=:st";
        jpql += " order by l.id desc";
        var q = em.createQuery(jpql, Loan.class);
        if (!all) q.setParameter("me", me);
        if (status != null && !status.isBlank()) {
            try { q.setParameter("st", com.example.cloans.domain.enums.LoanStatus.valueOf(status.trim().toUpperCase())); }
            catch (IllegalArgumentException e) { throw ApiException.badRequest("未知状态: " + status); }
        }
        return q.getResultList().stream().map(Views::loan).toList();
    }

    @GET @Path("loans/{id}")
    public Object one(@PathParam("id") long id) {
        var me = identity.current();
        Loan l = loans.mustLoan(id);
        if (me.role == Role.EMPLOYEE && !l.borrower.id.equals(me.id))
            throw ApiException.forbidden("只能查看本人的借阅单");
        return Views.loan(l);
    }

    /** 员工提交借阅申请（用途） */
    @POST @Path("loans")
    @Consumes(MediaType.APPLICATION_JSON)
    public Object apply(Requests.ApplyRequest req) {
        Loan l = loans.apply(identity.current(), req);
        return Views.loan(l);
    }

    @POST @Path("loans/{id}/approve")
    public Object approve(@PathParam("id") long id) {
        return Views.loan(loans.approve(identity.current(), id));
    }

    @POST @Path("loans/{id}/reject")
    @Consumes(MediaType.APPLICATION_JSON)
    public Object reject(@PathParam("id") long id, Requests.RejectRequest req) {
        return Views.loan(loans.reject(identity.current(), id, req == null ? null : req.reason()));
    }

    @POST @Path("loans/{id}/cancel")
    public Object cancel(@PathParam("id") long id) {
        return Views.loan(loans.cancel(identity.current(), id));
    }

    /** 档案员登记交接：记录实际经手人，原件出库 */
    @POST @Path("loans/{id}/handover")
    public Object handover(@PathParam("id") long id) {
        return Views.loan(loans.handover(identity.current(), id));
    }

    /** 员工到期前申请续借 */
    @POST @Path("loans/{id}/renew")
    @Consumes(MediaType.APPLICATION_JSON)
    public Object renew(@PathParam("id") long id, Requests.RenewRequest req) {
        return Views.renewal(loans.requestRenewal(identity.current(), id, req == null ? null : req.days()));
    }

    @GET @Path("renewals")
    public Object renewals(@QueryParam("scope") String scope) {
        var me = identity.current();
        boolean all = !"mine".equals(scope);
        if (all && me.role == Role.EMPLOYEE)
            throw ApiException.forbidden("仅管理人员/档案员可查看全部续借申请");
        var q = em.createQuery("select r from RenewalRequest r order by r.id desc", RenewalRequest.class);
        List<RenewalRequest> rs = q.getResultList();
        return rs.stream().filter(r -> !all || true)
                .filter(r -> all || r.requestedBy.id.equals(me.id))
                .map(Views::renewal).toList();
    }

    @POST @Path("renewals/{id}/decision")
    @Consumes(MediaType.APPLICATION_JSON)
    public Object renewalDecision(@PathParam("id") long id, Requests.DecisionRequest req) {
        boolean approve = req != null && Boolean.TRUE.equals(req.approve());
        return Views.renewal(loans.decideRenewal(identity.current(), id, approve, req == null ? null : req.note()));
    }

    /** 管理人员对逾期单催还跟进（本地模拟提醒） */
    @POST @Path("loans/{id}/remind")
    @Consumes(MediaType.APPLICATION_JSON)
    public Object remind(@PathParam("id") long id, Map<String, String> body) {
        var me = identity.current();
        if (me.role != Role.ADMIN) throw ApiException.forbidden("需要管理人员权限");
        loans.remind(loans.mustLoan(id), me, body == null ? null : body.get("message"));
        return D.of("ok", true);
    }

    /** 手动触发一次逾期/临期扫描 */
    @POST @Path("admin/overdue-sweep")
    public Object sweep() {
        var me = identity.current();
        if (me.role != Role.ADMIN && me.role != Role.ARCHIVIST)
            throw ApiException.forbidden("需要管理人员/档案员权限");
        int n = loans.overdueSweep();
        return D.of("overdueMarked", n);
    }

    /** 某借阅单的逐页核验历史（各轮次保留） */
    @GET @Path("loans/{id}/page-checks")
    public Object pageChecks(@PathParam("id") long id) {
        var me = identity.current();
        Loan l = loans.mustLoan(id);
        if (me.role == Role.EMPLOYEE && !l.borrower.id.equals(me.id))
            throw ApiException.forbidden("只能查看本人的借阅单");
        return em.createQuery("select p from PageCheck p where p.loan=:l order by p.round, p.pageNo", PageCheck.class)
                .setParameter("l", l).getResultList().stream().map(Views::pageCheck).toList();
    }
}
