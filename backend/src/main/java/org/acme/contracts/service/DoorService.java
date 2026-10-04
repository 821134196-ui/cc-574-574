package org.acme.contracts.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.acme.contracts.model.DoorEvent;
import org.acme.contracts.model.Employee;
import org.acme.contracts.model.Enums;
import org.acme.contracts.model.Loan;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * 门禁接口本地模拟：根据员工号生成稳定卡号，档案库门对 ARCHIVIST/ADMIN 放行。
 * 真实部署时替换此服务的 grant 实现即可对接门禁系统。
 */
@ApplicationScoped
public class DoorService {

    private static final Logger LOG = Logger.getLogger(DoorService.class);

    @Transactional
    public DoorEvent record(Loan loan, Enums.DoorDirection direction, Employee operator, String handler) {
        boolean granted = operator.role == Enums.Role.ARCHIVIST || operator.role == Enums.Role.ADMIN;
        String cardNo = "CARD-" + String.format("%06d", Math.abs(operator.employeeNo.hashCode()) % 1_000_000);

        DoorEvent event = new DoorEvent();
        event.loan = loan;
        event.direction = direction;
        event.person = handler;
        event.cardNo = cardNo;
        event.granted = granted;
        event.persist();

        LOG.infof("[模拟门禁] %s %s 经手人=%s 刷卡=%s 放行=%s",
                direction, loan.contract.code, handler, cardNo, granted);
        if (!granted) {
            throw ApiException.forbidden("门禁拒绝：当前操作人无档案库区权限");
        }
        return event;
    }

    public List<DoorEvent> listForLoan(Long loanId) {
        return DoorEvent.find("loan.id", loanId).list();
    }
}
