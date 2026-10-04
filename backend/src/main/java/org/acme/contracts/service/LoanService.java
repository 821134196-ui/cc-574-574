package org.acme.contracts.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.acme.contracts.model.*;
import org.acme.contracts.repo.ContractRepository;
import org.acme.contracts.repo.EmployeeRepository;
import org.acme.contracts.repo.LoanRepository;
import org.jboss.logging.Logger;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 借阅核心业务规则：
 * 1. 申请：密级权限校验 + 用途必填；
 * 2. 一份原件在途唯一（数据库部分唯一索引兜底，并发下只有一单成功）；
 * 3. 审批：管理员不能批准自己的借阅申请；批准锁定密级/岗位版本与到期日；
 * 4. 出库：档案员从盒位取出，登记实际经手人，写交接链 + 模拟门禁；
 * 5. 续借：仅到期前可申请；调岗/密级变化导致待重核验时先重验权限；
 * 6. 归还：逐页核验，缺页/盖章页缺失/损坏 -> ANOMALY，不得签为已还；
 * 7. 异常由管理员处理闭环后才可归还完成。
 *
 * 写方法都在事务内构建并返回视图 DTO，避免事务提交后读取懒加载关联。
 */
@ApplicationScoped
public class LoanService {

    private static final Logger LOG = Logger.getLogger(LoanService.class);

    @Inject
    LoanRepository loans;

    @Inject
    ContractRepository contracts;

    @Inject
    EmployeeRepository employees;

    @Inject
    AuditService audit;

    @Inject
    DoorService door;

    // ---------------- 申请 ----------------

    @Transactional
    public LoanView apply(Employee borrowerIn, Long contractId, String purpose, LocalDate dueDate) {
        Employee borrower = ref(borrowerIn);
        if (purpose == null || purpose.isBlank()) {
            throw ApiException.badRequest("借阅用途不能为空");
        }
        if (dueDate == null || !dueDate.isAfter(LocalDate.now())) {
            throw ApiException.badRequest("到期日必须晚于今天");
        }
        Contract c = contracts.findById(contractId);
        if (c == null) {
            throw ApiException.notFound("合同不存在: " + contractId);
        }
        if (borrower.clearance.level() < c.secrecy.level()) {
            throw ApiException.forbidden("借阅权限不足：本人密级 " + borrower.clearance.label()
                    + " 低于合同密级 " + c.secrecy.label());
        }
        if (loans.findActiveByContract(c) != null) {
            throw ApiException.conflict("合同 " + c.code + " 已有在途借阅单，不能同时借给两人");
        }

        Loan loan = new Loan();
        loan.contract = c;
        loan.borrower = borrower;
        loan.purpose = purpose.trim();
        loan.dueDate = dueDate;
        loan.status = Enums.LoanStatus.PENDING;
        loan.active = true;
        try {
            loans.persist(loan);
            loans.flush();
        } catch (PersistenceException e) {
            // 并发场景：另一个事务已先占用该合同，部分唯一索引 ux_loan_active 拒绝
            if (isUniqueViolation(e)) {
                throw ApiException.conflict("合同 " + c.code + " 刚被其他借阅单占用，请刷新后重试");
            }
            throw e;
        }
        audit.append("LOAN_APPLY", borrower, loan.id, c.id, borrower.id,
                "{\"purpose\":\"" + esc(purpose) + "\",\"dueDate\":\"" + dueDate + "\"}");
        LOG.infof("借阅申请 #%d 合同=%s 借阅人=%s", loan.id, c.code, borrower.name);
        return LoanView.of(loan);
    }

    // ---------------- 审批 ----------------

    @Transactional
    public LoanView approve(Employee adminIn, Long loanId, boolean approved, String rejectReason) {
        Employee admin = ref(adminIn);
        Loan loan = mustGet(loanId);
        if (loan.status != Enums.LoanStatus.PENDING) {
            throw ApiException.badRequest("仅待审批借阅单可审批，当前状态: " + loan.status);
        }
        if (admin.role != Enums.Role.ADMIN) {
            throw ApiException.forbidden("仅管理人员可审批");
        }
        // 管理员不能批准自己的借阅
        if (loan.borrower.id.equals(admin.id)) {
            throw ApiException.forbidden("管理员不能批准自己提交的借阅申请，请由其他管理人员审批");
        }
        if (approved) {
            if (loan.borrower.clearance.level() < loan.contract.secrecy.level()) {
                throw ApiException.forbidden("借阅人密级已不满足合同要求，不能批准");
            }
            loan.status = Enums.LoanStatus.APPROVED;
            loan.approvedBy = admin;
            loan.approvedAt = java.time.Instant.now();
            loan.secrecyAtApproval = loan.contract.secrecy;
            loan.postVersionAtApproval = loan.borrower.postVersion;
            loan.reverify = Enums.ReverifyStatus.NONE;
            audit.append("LOAN_APPROVE", admin, loan.id, loan.contract.id, loan.borrower.id,
                    "{\"dueDate\":\"" + loan.dueDate + "\"}");
        } else {
            loan.status = Enums.LoanStatus.REJECTED;
            loan.active = false;
            loan.rejectReason = rejectReason == null ? "" : rejectReason.trim();
            loan.approvedBy = admin;
            loan.approvedAt = java.time.Instant.now();
            audit.append("LOAN_REJECT", admin, loan.id, loan.contract.id, loan.borrower.id,
                    "{\"reason\":\"" + esc(loan.rejectReason) + "\"}");
        }
        return LoanView.of(loan);
    }

    // ---------------- 出库 ----------------

    @Transactional
    public LoanView issue(Employee archivistIn, Long loanId, String handler) {
        Employee archivist = ref(archivistIn);
        Loan loan = mustGet(loanId);
        if (archivist.role != Enums.Role.ARCHIVIST) {
            throw ApiException.forbidden("仅法务档案员可办理原件出库");
        }
        if (loan.status != Enums.LoanStatus.APPROVED) {
            throw ApiException.badRequest("仅已批准待出库的借阅单可出库，当前状态: " + loan.status);
        }
        if (handler == null || handler.isBlank()) {
            throw ApiException.badRequest("必须登记实际经手人");
        }
        loan.status = Enums.LoanStatus.ISSUED;
        loan.issuedBy = archivist;
        loan.issuedAt = java.time.Instant.now();
        loan.issueHandler = handler.trim();

        door.record(loan, Enums.DoorDirection.OUT, archivist, loan.issueHandler);
        appendHandover(loan, Enums.HandoverKind.ISSUE, archivist, loan.issueHandler,
                "从盒位 " + loan.contract.boxLocation + " 取出原件");
        audit.append("LOAN_ISSUE", archivist, loan.id, loan.contract.id, loan.borrower.id,
                "{\"handler\":\"" + esc(loan.issueHandler) + "\",\"box\":\""
                        + esc(loan.contract.boxLocation) + "\"}");
        LOG.infof("出库 #%d 合同=%s 经手人=%s 档案员=%s",
                loan.id, loan.contract.code, loan.issueHandler, archivist.name);
        return LoanView.of(loan);
    }

    // ---------------- 续借 ----------------

    @Transactional
    public Map<String, Object> requestRenewal(Employee borrowerIn, Long loanId,
                                              LocalDate newDueDate, String reason) {
        Employee borrower = ref(borrowerIn);
        Loan loan = mustGet(loanId);
        if (!loan.borrower.id.equals(borrower.id) && borrower.role != Enums.Role.ADMIN) {
            throw ApiException.forbidden("只能为本人的借阅单申请续借");
        }
        if (loan.status != Enums.LoanStatus.ISSUED && loan.status != Enums.LoanStatus.OVERDUE) {
            throw ApiException.badRequest("仅借阅中的借阅单可续借，当前状态: " + loan.status);
        }
        LocalDate today = LocalDate.now();
        // 硬拦截：逾期（到期日早于今天）不允许续借
        if (loan.dueDate.isBefore(today)) {
            throw ApiException.badRequest("借阅已于 " + loan.dueDate + " 逾期，不能续借，请先归还或联系管理员跟进");
        }
        if (newDueDate == null || !newDueDate.isAfter(loan.dueDate)) {
            throw ApiException.badRequest("新到期日必须晚于当前到期日 " + loan.dueDate);
        }
        // 调岗或密级变化后必须先重新核验权限
        refreshReverify(loan);
        if (loan.reverify == Enums.ReverifyStatus.REQUIRED) {
            throw ApiException.conflict("借阅人调岗或合同密级已变更，须管理员重新核验权限后才能续借");
        }
        Renewal r = new Renewal();
        r.loan = loan;
        r.oldDueDate = loan.dueDate;
        r.newDueDate = newDueDate;
        r.reason = reason == null ? "" : reason.trim();
        r.status = Enums.RenewalStatus.PENDING;
        r.persist();
        r.flush();
        audit.append("RENEW_APPLY", borrower, loan.id, loan.contract.id, borrower.id,
                "{\"oldDue\":\"" + r.oldDueDate + "\",\"newDue\":\"" + newDueDate + "\"}");
        return Views.renewal(r);
    }

    @Transactional
    public Map<String, Object> decideRenewal(Employee adminIn, Long renewalId,
                                             boolean approved, String rejectReason) {
        Employee admin = ref(adminIn);
        Renewal r = Renewal.findById(renewalId);
        if (r == null) {
            throw ApiException.notFound("续借申请不存在: " + renewalId);
        }
        if (admin.role != Enums.Role.ADMIN) {
            throw ApiException.forbidden("仅管理人员可审批续借");
        }
        if (r.status != Enums.RenewalStatus.PENDING) {
            throw ApiException.badRequest("该续借申请已处理");
        }
        Loan loan = r.loan;
        if (admin.id.equals(loan.borrower.id)) {
            throw ApiException.forbidden("管理员不能批准自己的续借申请");
        }
        if (loan.dueDate.isBefore(LocalDate.now())) {
            throw ApiException.badRequest("审批时借阅已逾期，续借不予通过");
        }
        refreshReverify(loan);
        if (approved && loan.reverify == Enums.ReverifyStatus.REQUIRED) {
            throw ApiException.conflict("借阅人调岗或合同密级已变更，须先重新核验权限");
        }
        r.status = approved ? Enums.RenewalStatus.APPROVED : Enums.RenewalStatus.REJECTED;
        r.decidedBy = admin;
        r.decidedAt = java.time.Instant.now();
        r.rejectReason = rejectReason == null ? "" : rejectReason.trim();
        if (approved) {
            loan.dueDate = r.newDueDate;
            loan.renewalCount++;
            if (loan.status == Enums.LoanStatus.OVERDUE) {
                loan.status = Enums.LoanStatus.ISSUED;
            }
            audit.append("RENEW_APPROVE", admin, loan.id, loan.contract.id, loan.borrower.id,
                    "{\"newDue\":\"" + r.newDueDate + "\"}");
        } else {
            audit.append("RENEW_REJECT", admin, loan.id, loan.contract.id, loan.borrower.id,
                    "{\"reason\":\"" + esc(r.rejectReason) + "\"}");
        }
        return Views.renewal(r);
    }

    // ---------------- 归还核验 ----------------

    @Transactional
    public ReturnOutcome returnLoan(Employee archivistIn, Long loanId, ReturnCheckInput in) {
        Employee archivist = ref(archivistIn);
        Loan loan = mustGet(loanId);
        if (archivist.role != Enums.Role.ARCHIVIST) {
            throw ApiException.forbidden("仅法务档案员可办理归还核验");
        }
        if (loan.status != Enums.LoanStatus.ISSUED
                && loan.status != Enums.LoanStatus.OVERDUE
                && loan.status != Enums.LoanStatus.ANOMALY) {
            throw ApiException.badRequest("当前借阅单状态不支持归还: " + loan.status);
        }
        if (in.handler() == null || in.handler().isBlank()) {
            throw ApiException.badRequest("必须登记实际送还经手人");
        }
        if (in.pagesPresent() < 0 || in.pagesPresent() > loan.contract.pages) {
            throw ApiException.badRequest("实到页数应在 0 与总页数 " + loan.contract.pages + " 之间");
        }

        ReturnCheck check = new ReturnCheck();
        check.loan = loan;
        check.archivist = archivist;
        check.pagesPresent = in.pagesPresent();
        check.missingPages = in.missingPages() == null ? "" : in.missingPages().trim();
        check.missingAttachments = in.missingAttachments() == null ? "" : in.missingAttachments().trim();
        check.stampedPagesOk = in.stampedPagesOk();
        check.damageDesc = in.damageDesc() == null ? "" : in.damageDesc().trim();
        check.note = in.note() == null ? "" : in.note().trim();

        Enums.CheckResult result = evaluate(loan, check);
        check.result = result;
        check.persist();
        check.flush();

        loan.returnHandler = in.handler().trim();

        // 无论是否异常，收回动作都登记交接与门禁入库
        door.record(loan, Enums.DoorDirection.IN, archivist, loan.returnHandler);
        appendHandover(loan, Enums.HandoverKind.RETURN, archivist, loan.returnHandler,
                "核验结果: " + resultLabel(result));

        if (result == Enums.CheckResult.PASS) {
            // 若此前还有未闭环异常，仍不能签为已还
            long openAnomalies = Anomaly.count("loan = ?1 and status = ?2",
                    loan, Enums.AnomalyStatus.OPEN);
            if (openAnomalies > 0) {
                loan.status = Enums.LoanStatus.ANOMALY;
                audit.append("RETURN_CHECK_PASS_PENDING_ANOMALY", archivist, loan.id,
                        loan.contract.id, loan.borrower.id, "{}");
                // 正常返回让事务提交（保留核验/交接/异常态），由资源层转 409
                return new ReturnOutcome(LoanView.of(loan),
                        "本次核验通过，但存在未闭环的异常记录，需管理员处理后才能签为已还");
            }
            closeReturned(loan, archivist);
            LOG.infof("归还完成 #%d 合同=%s", loan.id, loan.contract.code);
            return new ReturnOutcome(LoanView.of(loan), null);
        } else {
            // 缺页/损坏：进入异常处理，禁止签为已还（保持 active，不可再借）
            loan.status = Enums.LoanStatus.ANOMALY;
            Anomaly anomaly = new Anomaly();
            anomaly.loan = loan;
            anomaly.returnCheck = check;
            anomaly.kind = result;
            anomaly.description = buildAnomalyDescription(result, check);
            anomaly.status = Enums.AnomalyStatus.OPEN;
            anomaly.persist();
            audit.append("RETURN_ANOMALY", archivist, loan.id, loan.contract.id, loan.borrower.id,
                    "{\"result\":\"" + result + "\",\"desc\":\"" + esc(anomaly.description) + "\"}");
            LOG.warnf("归还异常 #%d 合同=%s 结果=%s", loan.id, loan.contract.code, result);
            // 事务正常提交以保存异常单与核验记录，由资源层转 409
            return new ReturnOutcome(LoanView.of(loan),
                    "核验发现 " + resultLabel(result) + "，已转入异常处理，不能签为已还");
        }
    }

    /** 归还结果：blockedMessage 非空表示被拦截（资源层据此返回 409），此时状态变更已提交。 */
    public record ReturnOutcome(LoanView loan, String blockedMessage) {
        public boolean blocked() {
            return blockedMessage != null;
        }
    }

    private Enums.CheckResult evaluate(Loan loan, ReturnCheck check) {
        boolean missing = check.pagesPresent < loan.contract.pages
                || !check.missingPages.isBlank()
                || !check.missingAttachments.isBlank()
                || !check.stampedPagesOk;
        boolean damaged = !check.damageDesc.isBlank();
        if (missing) {
            return Enums.CheckResult.MISSING_PAGE;
        }
        if (damaged) {
            return Enums.CheckResult.DAMAGED;
        }
        return Enums.CheckResult.PASS;
    }

    // ---------------- 异常处理闭环 ----------------

    @Transactional
    public Map<String, Object> resolveAnomaly(Employee adminIn, Long anomalyId,
                                              String resolution, boolean completeReturn) {
        Employee admin = ref(adminIn);
        Anomaly a = Anomaly.findById(anomalyId);
        if (a == null) {
            throw ApiException.notFound("异常记录不存在: " + anomalyId);
        }
        if (admin.role != Enums.Role.ADMIN) {
            throw ApiException.forbidden("仅管理人员可闭环异常");
        }
        if (a.status == Enums.AnomalyStatus.RESOLVED) {
            throw ApiException.badRequest("异常已闭环");
        }
        if (resolution == null || resolution.isBlank()) {
            throw ApiException.badRequest("必须填写异常处理结论");
        }
        a.status = Enums.AnomalyStatus.RESOLVED;
        a.resolvedBy = admin;
        a.resolvedAt = java.time.Instant.now();
        a.resolution = resolution.trim();

        audit.append("ANOMALY_RESOLVE", admin, a.loan.id, a.loan.contract.id, a.loan.borrower.id,
                "{\"completeReturn\":" + completeReturn + ",\"resolution\":\"" + esc(resolution) + "\"}");

        if (completeReturn) {
            long stillOpen = Anomaly.count("loan = ?1 and status = ?2",
                    a.loan, Enums.AnomalyStatus.OPEN);
            if (stillOpen == 0) {
                closeReturned(a.loan, admin);
            }
        }
        return Views.anomaly(a);
    }

    private void closeReturned(Loan loan, Employee actor) {
        loan.status = Enums.LoanStatus.RETURNED;
        loan.active = false;
        loan.returnedAt = java.time.Instant.now();
        audit.append("LOAN_RETURNED", actor, loan.id, loan.contract.id, loan.borrower.id,
                "{\"handler\":\"" + esc(loan.returnHandler == null ? "" : loan.returnHandler) + "\"}");
    }

    // ---------------- 权限重核验 ----------------

    /** 比对审批时锁定的密级/岗位版本，发生变化则标记待重核验。 */
    @Transactional
    public void refreshReverify(Loan loan) {
        boolean secrecyChanged = loan.secrecyAtApproval != null
                && loan.contract.secrecy != loan.secrecyAtApproval;
        boolean postChanged = loan.postVersionAtApproval != null
                && loan.borrower.postVersion != loan.postVersionAtApproval;
        if ((secrecyChanged || postChanged) && loan.reverify != Enums.ReverifyStatus.OK) {
            loan.reverify = Enums.ReverifyStatus.REQUIRED;
        }
    }

    /** 管理员对调岗/密级变化后的借阅单重新核验权限。 */
    @Transactional
    public LoanView reverify(Employee adminIn, Long loanId) {
        Employee admin = ref(adminIn);
        if (admin.role != Enums.Role.ADMIN) {
            throw ApiException.forbidden("仅管理人员可重新核验权限");
        }
        Loan loan = mustGet(loanId);
        refreshReverify(loan);
        if (loan.reverify != Enums.ReverifyStatus.REQUIRED) {
            throw ApiException.badRequest("该借阅单无需重新核验");
        }
        if (loan.borrower.clearance.level() < loan.contract.secrecy.level()) {
            audit.append("REVERIFY_FAIL", admin, loan.id, loan.contract.id, loan.borrower.id,
                    "{\"clearance\":\"" + loan.borrower.clearance + "\",\"secrecy\":\""
                            + loan.contract.secrecy + "\"}");
            throw ApiException.conflict("重核验未通过：借阅人现密级 " + loan.borrower.clearance.label()
                    + " 低于合同密级 " + loan.contract.secrecy.label()
                    + "，续借被拦截，应立即收回原件");
        }
        loan.reverify = Enums.ReverifyStatus.OK;
        loan.secrecyAtApproval = loan.contract.secrecy;
        loan.postVersionAtApproval = loan.borrower.postVersion;
        audit.append("REVERIFY_OK", admin, loan.id, loan.contract.id, loan.borrower.id, "{}");
        return LoanView.of(loan);
    }

    // ---------------- 交接链 ----------------

    private void appendHandover(Loan loan, Enums.HandoverKind kind, Employee archivist,
                                String handler, String note) {
        long count = Handover.count("loan", loan);
        Handover last = Handover.find("loan = ?1 order by seq desc", loan).firstResult();
        Handover h = new Handover();
        h.loan = loan;
        h.seq = (int) count + 1;
        h.prevId = last == null ? null : last.id;
        h.kind = kind;
        h.archivist = archivist;
        h.handler = handler;
        h.note = note;
        h.persist();
    }

    // ---------------- 查询 ----------------

    private Employee ref(Employee e) {
        return employees.findById(e.id);
    }

    public Loan mustGet(Long id) {
        Loan loan = loans.findById(id);
        if (loan == null) {
            throw ApiException.notFound("借阅单不存在: " + id);
        }
        return loan;
    }

    @Transactional
    public LoanView viewOf(Long id) {
        return LoanView.of(mustGet(id));
    }

    @Transactional
    public List<LoanView> activeLoans() {
        return loans.find("active = true order by requestedAt desc").list().stream()
                .map(LoanView::of).toList();
    }

    @Transactional
    public List<LoanView> allLoans() {
        return loans.listAll().stream().map(LoanView::of)
                .sorted((a, b) -> b.requestedAt().compareTo(a.requestedAt())).toList();
    }

    @Transactional
    public List<LoanView> loansOfBorrower(Long employeeId) {
        return loans.find("borrower.id = ?1 order by requestedAt desc", employeeId).list().stream()
                .map(LoanView::of).toList();
    }

    /** 管理人员视图：每份合同当前由谁保管、何时应归还。 */
    @Transactional
    public List<CustodyView> custody() {
        return contracts.listAll().stream().map(c -> {
            Loan active = loans.findActiveByContract(c);
            if (active == null) {
                return new CustodyView(c.id, c.code, c.title, c.secrecy.label(), c.boxLocation,
                        "在库", null, null, null, null, null, false);
            }
            return new CustodyView(c.id, c.code, c.title, c.secrecy.label(), c.boxLocation,
                    LoanView.statusLabel(active.status),
                    active.borrower.id, active.borrower.name, active.issueHandler,
                    active.dueDate, active.id, active.dueDate.isBefore(LocalDate.now()));
        }).toList();
    }

    private boolean isUniqueViolation(Throwable t) {
        while (t != null) {
            if (t instanceof SQLException sq && sq.getMessage() != null
                    && sq.getMessage().contains("UNIQUE")) {
                return true;
            }
            if (t instanceof org.hibernate.exception.ConstraintViolationException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private static String resultLabel(Enums.CheckResult r) {
        return switch (r) {
            case PASS -> "核验通过";
            case MISSING_PAGE -> "缺页/附件或盖章页缺失";
            case DAMAGED -> "原件损坏";
        };
    }

    private static String buildAnomalyDescription(Enums.CheckResult r, ReturnCheck c) {
        StringBuilder sb = new StringBuilder(resultLabel(r));
        if (!c.missingPages.isBlank()) {
            sb.append("；缺页页码: ").append(c.missingPages);
        }
        if (!c.missingAttachments.isBlank()) {
            sb.append("；缺失附件: ").append(c.missingAttachments);
        }
        if (!c.stampedPagesOk) {
            sb.append("；盖章页不齐全");
        }
        if (!c.damageDesc.isBlank()) {
            sb.append("；损坏情况: ").append(c.damageDesc);
        }
        return sb.toString();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }

    /** 归还核验入参。 */
    public record ReturnCheckInput(
            String handler,
            int pagesPresent,
            String missingPages,
            String missingAttachments,
            boolean stampedPagesOk,
            String damageDesc,
            String note) {
    }

    /** 合同当前保管情况视图。 */
    public record CustodyView(
            Long contractId,
            String contractCode,
            String contractTitle,
            String secrecy,
            String boxLocation,
            String status,
            Long borrowerId,
            String borrowerName,
            String handler,
            LocalDate dueDate,
            Long loanId,
            boolean overdue) {
    }
}
