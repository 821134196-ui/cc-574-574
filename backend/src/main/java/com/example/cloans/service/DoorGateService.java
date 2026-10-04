package com.example.cloans.service;

import com.example.cloans.domain.Contract;
import com.example.cloans.domain.Loan;
import com.example.cloans.domain.User;
import com.example.cloans.domain.enums.NotificationType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * 门禁接口本地模拟。
 * 真实环境对接档案室门禁/安检门：原件出库必须与借阅单、借阅人一致才放行。
 * 这里仅生成放行记录（以提醒形式留痕），不调用任何外部服务。
 */
@ApplicationScoped
public class DoorGateService {

    @Inject
    NotificationService notifications;

    public void issueExitPass(Loan loan) {
        User b = loan.borrower;
        Contract c = loan.contract;
        String msg = String.format(
                "【门禁模拟·出库放行】借阅单#%d 员工「%s」凭审批携带合同《%s》(%s) 于应还日 %s 前有效，" +
                        "放行通道：档案室东门",
                loan.id, b.name, c.title, c.code, loan.dueDate);
        notifications.notify(b, NotificationType.GATE, "门禁出库放行", msg, loan.id);
    }

    public void issueReturnPass(Loan loan) {
        User b = loan.borrower;
        String msg = String.format(
                "【门禁模拟·入库核销】借阅单#%d 员工「%s」归还合同《%s》，门禁核销出库记录。",
                loan.id, b.name, loan.contract.title);
        notifications.notify(b, NotificationType.GATE, "门禁入库核销", msg, loan.id);
    }
}
