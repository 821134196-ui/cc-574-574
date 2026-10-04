package org.acme.contracts.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.contracts.model.Enums;
import org.acme.contracts.model.Loan;
import org.acme.contracts.model.Reminder;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.List;

/**
 * 提醒本地模拟：定时扫描在途借阅单 —— 到期前 1 天 DUE_SOON，
 * 到期当日起 OVERDUE（借阅单同时标记 OVERDUE），并记录跟进提示。
 */
@ApplicationScoped
public class ReminderService {

    private static final Logger LOG = Logger.getLogger(ReminderService.class);

    @Scheduled(cron = "{contracts.reminder.cron}")
    @Transactional
    public void sweep() {
        LocalDate today = LocalDate.now();
        List<Loan> active = Loan.list("status in ?1",
                List.of(Enums.LoanStatus.ISSUED, Enums.LoanStatus.OVERDUE));

        for (Object o : active) {
            Loan loan = (Loan) o;
            if (loan.dueDate == null) {
                continue;
            }
            if (loan.dueDate.isBefore(today) && loan.status != Enums.LoanStatus.OVERDUE) {
                loan.status = Enums.LoanStatus.OVERDUE;
                emit(loan, Enums.ReminderKind.OVERDUE, today,
                        "合同 " + loan.contract.code + " 已于 " + loan.dueDate + " 逾期，请立即跟进借阅人 "
                                + loan.borrower.name);
            } else if (loan.dueDate.equals(today) || loan.dueDate.equals(today.plusDays(1))) {
                emit(loan, Enums.ReminderKind.DUE_SOON, today,
                        "合同 " + loan.contract.code + " 将于 " + loan.dueDate + " 到期，请提醒归还");
            }
            if (loan.status == Enums.LoanStatus.OVERDUE) {
                // 逾期期间每 3 天生成一条跟进提醒
                int daysOver = (int) java.time.temporal.ChronoUnit.DAYS.between(loan.dueDate, today);
                if (daysOver > 0 && daysOver % 3 == 0) {
                    emit(loan, Enums.ReminderKind.FOLLOWUP, today,
                            "合同 " + loan.contract.code + " 已逾期 " + daysOver + " 天，持续跟进中");
                }
            }
        }
    }

    private void emit(Loan loan, Enums.ReminderKind kind, LocalDate today, String message) {
        String dedup = loan.id + ":" + kind + ":" + today;
        long exists = Reminder.count("dedupKey", dedup);
        if (exists > 0) {
            return;
        }
        Reminder r = new Reminder();
        r.loan = loan;
        r.kind = kind;
        r.message = message;
        r.dueDate = loan.dueDate;
        r.dedupKey = dedup;
        r.persist();
        LOG.warn("[模拟提醒] " + message);
    }
}
