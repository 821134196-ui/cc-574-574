package org.acme.contracts.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.acme.contracts.model.*;
import org.acme.contracts.security.CurrentUser;
import org.acme.contracts.service.LoanService;
import org.acme.contracts.service.LoanView;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Path("/api/loans")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LoanResource {

    @Inject
    LoanService service;

    @Inject
    org.acme.contracts.service.LoanCreationLock creationLock;

    @Inject
    CurrentUser currentUser;

    public record ApplyBody(Long contractId, String purpose, LocalDate dueDate) {
    }

    public record ApproveBody(Boolean approved, String rejectReason) {
    }

    public record IssueBody(String handler) {
    }

    public record RenewBody(LocalDate newDueDate, String reason) {
    }

    public record RenewDecisionBody(Boolean approved, String rejectReason) {
    }

    public record ResolveBody(String resolution, Boolean completeReturn) {
    }

    // ---------- 借阅 ----------

    @POST
    public LoanView apply(ApplyBody body) {
        Employee user = currentUser.requireAny();
        // 串行化“检查在途 -> 插入 -> 提交”，保证并发下后来者得到 409 而非提交期 500
        return creationLock.withLock(
                () -> service.apply(user, body.contractId(), body.purpose(), body.dueDate()));
    }

    @POST
    @Path("/{id}/approve")
    public LoanView approve(@PathParam("id") Long id, ApproveBody body) {
        Employee admin = currentUser.requireRole(Enums.Role.ADMIN);
        boolean ok = !Boolean.FALSE.equals(body.approved());
        return service.approve(admin, id, ok, body.rejectReason());
    }

    @POST
    @Path("/{id}/issue")
    public LoanView issue(@PathParam("id") Long id, IssueBody body) {
        Employee archivist = currentUser.requireRole(Enums.Role.ARCHIVIST);
        return service.issue(archivist, id, body.handler());
    }

    @POST
    @Path("/{id}/return")
    public LoanView doReturn(@PathParam("id") Long id, LoanService.ReturnCheckInput body) {
        Employee archivist = currentUser.requireRole(Enums.Role.ARCHIVIST);
        // 缺页/损坏时服务层先提交异常单与核验记录，再在此转成 409
        LoanService.ReturnOutcome outcome = service.returnLoan(archivist, id, body);
        if (outcome.blocked()) {
            throw org.acme.contracts.service.ApiException.conflict(outcome.blockedMessage());
        }
        return outcome.loan();
    }

    // ---------- 续借 ----------

    @POST
    @Path("/{id}/renewals")
    public Map<String, Object> requestRenewal(@PathParam("id") Long id, RenewBody body) {
        Employee user = currentUser.requireAny();
        return service.requestRenewal(user, id, body.newDueDate(), body.reason());
    }

    @POST
    @Path("/renewals/{renewalId}/decision")
    public Map<String, Object> decideRenewal(@PathParam("renewalId") Long renewalId, RenewDecisionBody body) {
        Employee admin = currentUser.requireRole(Enums.Role.ADMIN);
        boolean ok = !Boolean.FALSE.equals(body.approved());
        return service.decideRenewal(admin, renewalId, ok, body.rejectReason());
    }

    @GET
    @Path("/{id}/renewals")
    public List<Map<String, Object>> renewals(@PathParam("id") Long id) {
        currentUser.requireAny();
        java.util.List<Renewal> list = Renewal.list("loan.id = ?1 order by requestedAt", id);
        return list.stream().map(org.acme.contracts.service.Views::renewal).toList();
    }

    // ---------- 权限重核验 ----------

    @POST
    @Path("/{id}/reverify")
    @Consumes(MediaType.WILDCARD)
    public LoanView reverify(@PathParam("id") Long id) {
        Employee admin = currentUser.requireRole(Enums.Role.ADMIN);
        return service.reverify(admin, id);
    }

    // ---------- 异常 ----------

    @GET
    @Path("/anomalies")
    public List<Map<String, Object>> anomalies(@QueryParam("status") String status) {
        currentUser.requireAny();
        java.util.List<Anomaly> list = "OPEN".equalsIgnoreCase(status)
                ? Anomaly.list("status = ?1 order by openedAt desc", Enums.AnomalyStatus.OPEN)
                : Anomaly.list("order by openedAt desc");
        return list.stream().map(org.acme.contracts.service.Views::anomaly).toList();
    }

    @POST
    @Path("/anomalies/{anomalyId}/resolve")
    public Map<String, Object> resolve(@PathParam("anomalyId") Long anomalyId, ResolveBody body) {
        Employee admin = currentUser.requireRole(Enums.Role.ADMIN);
        boolean complete = Boolean.TRUE.equals(body.completeReturn());
        return service.resolveAnomaly(admin, anomalyId, body.resolution(), complete);
    }

    // ---------- 查询 ----------

    @GET
    public List<LoanView> list(@QueryParam("scope") String scope) {
        Employee me = currentUser.requireAny();
        if ("mine".equals(scope)) {
            return service.loansOfBorrower(me.id);
        }
        if (me.role == Enums.Role.EMPLOYEE) {
            // 普通员工只能看到自己的借阅
            return service.loansOfBorrower(me.id);
        }
        return "active".equals(scope) ? service.activeLoans() : service.allLoans();
    }

    @GET
    @Path("/{id}")
    public LoanView get(@PathParam("id") Long id) {
        Employee me = currentUser.requireAny();
        Loan loan = service.mustGet(id);
        if (me.role == Enums.Role.EMPLOYEE && !loan.borrower.id.equals(me.id)) {
            throw org.acme.contracts.service.ApiException.forbidden("只能查看本人借阅单");
        }
        return service.viewOf(id);
    }

    /** 管理人员：每份合同现在由谁保管、何时应归还。 */
    @GET
    @Path("/custody/overview")
    public List<LoanService.CustodyView> custody() {
        currentUser.requireRole(Enums.Role.ADMIN);
        return service.custody();
    }

    // ---------- 交接/核验/门禁/审计：顺序追查 ----------

    @GET
    @Path("/{id}/handovers")
    public List<Map<String, Object>> handovers(@PathParam("id") Long id) {
        currentUser.requireAny();
        java.util.List<Handover> list = Handover.list("loan.id = ?1 order by seq", id);
        return list.stream().map(org.acme.contracts.service.Views::handover).toList();
    }

    @GET
    @Path("/{id}/checks")
    public List<Map<String, Object>> checks(@PathParam("id") Long id) {
        currentUser.requireAny();
        java.util.List<ReturnCheck> list = ReturnCheck.list("loan.id = ?1 order by createdAt", id);
        return list.stream().map(org.acme.contracts.service.Views::check).toList();
    }

    @GET
    @Path("/{id}/door-events")
    public List<Map<String, Object>> doorEvents(@PathParam("id") Long id) {
        currentUser.requireAny();
        java.util.List<DoorEvent> list = DoorEvent.list("loan.id = ?1 order by at", id);
        return list.stream().map(org.acme.contracts.service.Views::door).toList();
    }

    @GET
    @Path("/{id}/audit")
    public List<AuditLog> auditForLoan(@PathParam("id") Long id) {
        currentUser.requireAny();
        return AuditLog.list("loanId = ?1 order by seq", id);
    }

    @GET
    @Path("/audit/trail")
    public List<Map<String, Object>> auditTrail() {
        Employee me = currentUser.requireAny();
        if (me.role == Enums.Role.EMPLOYEE) {
            throw org.acme.contracts.service.ApiException.forbidden("仅档案员/管理员可查看全量审计链");
        }
        java.util.List<AuditLog> logs = AuditLog.list("order by seq");
        return logs.stream().map(a -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("seq", a.seq);
            m.put("id", a.id);
            m.put("prevId", a.prevId);
            m.put("action", a.action);
            m.put("loanId", a.loanId);
            m.put("contractId", a.contractId);
            m.put("employeeId", a.employeeId);
            m.put("actor", a.actorName);
            m.put("detail", a.detail);
            m.put("at", a.at);
            return m;
        }).toList();
    }
}
