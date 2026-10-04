package com.example.cloans.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/** 每分钟扫描临期/逾期借阅，产生提醒并翻转 OVERDUE 状态 */
@ApplicationScoped
public class OverdueScheduler {

    private static final Logger LOG = Logger.getLogger(OverdueScheduler.class);

    @Inject LoanService loans;

    @Scheduled(every = "60s", delayed = "5s")
    void tick() {
        try {
            loans.overdueSweep();
        } catch (Exception e) {
            LOG.warn("逾期扫描失败: " + e.getMessage());
        }
    }
}
