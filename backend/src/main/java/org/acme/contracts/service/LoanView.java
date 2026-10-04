package org.acme.contracts.service;

import org.acme.contracts.model.*;

import java.time.Instant;
import java.time.LocalDate;

/** 借阅单视图：借阅人、实际经手人、到期日同卡展示所需字段全部摊平。 */
public record LoanView(
        Long id,
        String status,
        String statusLabel,
        Long contractId,
        String contractCode,
        String contractTitle,
        String secrecy,
        String secrecyLabel,
        Integer contractPages,
        String boxLocation,
        Long borrowerId,
        String borrowerNo,
        String borrowerName,
        String borrowerPost,
        String purpose,
        Instant requestedAt,
        String approverName,
        Instant approvedAt,
        String rejectReason,
        String archivistName,
        Instant issuedAt,
        String issueHandler,
        String returnHandler,
        LocalDate dueDate,
        Instant returnedAt,
        String reverify,
        int renewalCount,
        boolean overdue
) {
    public static LoanView of(Loan l) {
        Contract c = l.contract;
        Employee b = l.borrower;
        boolean overdue = (l.status == Enums.LoanStatus.ISSUED || l.status == Enums.LoanStatus.OVERDUE)
                && l.dueDate != null && l.dueDate.isBefore(LocalDate.now());
        return new LoanView(
                l.id, l.status.name(), statusLabel(l.status),
                c.id, c.code, c.title,
                c.secrecy.name(), c.secrecy.label(), c.pages, c.boxLocation,
                b.id, b.employeeNo, b.name, b.post,
                l.purpose, l.requestedAt,
                l.approvedBy == null ? null : l.approvedBy.name, l.approvedAt, l.rejectReason,
                l.issuedBy == null ? null : l.issuedBy.name, l.issuedAt,
                l.issueHandler, l.returnHandler,
                l.dueDate, l.returnedAt,
                l.reverify.name(), l.renewalCount, overdue);
    }

    public static String statusLabel(Enums.LoanStatus s) {
        return switch (s) {
            case PENDING -> "待审批";
            case APPROVED -> "已批准待出库";
            case REJECTED -> "已驳回";
            case ISSUED -> "借阅中";
            case OVERDUE -> "已逾期";
            case ANOMALY -> "异常处理中";
            case RETURNED -> "已归还";
        };
    }
}
