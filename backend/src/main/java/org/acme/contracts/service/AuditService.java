package org.acme.contracts.service;

import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.contracts.model.AuditLog;
import org.acme.contracts.model.Employee;

import java.util.List;

/**
 * 顺序审计链：append 只追加；每条记录取全局 max(seq)+1 并链接 prevId。
 * 调用方须处于事务中，保证 seq 分配与业务变更原子提交。
 */
@ApplicationScoped
public class AuditService {

    @Transactional
    public AuditLog append(String action, Employee actor, Long loanId, Long contractId,
                           Long employeeId, String detail) {
        Long maxSeq = Panache.getEntityManager()
                .createQuery("select coalesce(max(a.seq),0) from AuditLog a", Long.class)
                .getSingleResult();
        AuditLog last = AuditLog.find("order by seq desc").firstResult();

        AuditLog log = new AuditLog();
        log.seq = maxSeq + 1;
        log.prevId = last == null ? null : last.id;
        log.action = action;
        log.loanId = loanId;
        log.contractId = contractId;
        log.employeeId = employeeId;
        log.actorId = actor == null ? 0L : actor.id;
        log.actorName = actor == null ? "系统" : actor.name;
        log.detail = detail;
        log.persist();
        return log;
    }

    public List<AuditLog> chainForLoan(Long loanId) {
        return AuditLog.find("loanId = ?1 order by seq", loanId).list();
    }

    public List<AuditLog> allChain() {
        return AuditLog.find("order by seq").list();
    }
}
