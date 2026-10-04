package org.acme.contracts.repo;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.contracts.model.Contract;
import org.acme.contracts.model.Loan;

@ApplicationScoped
public class LoanRepository implements PanacheRepository<Loan> {

    /** 查找某份合同当前在途借阅单（申请/批准/出库/逾期/异常均算占用）。 */
    public Loan findActiveByContract(Contract contract) {
        return find("contract = ?1 and active = true", contract).firstResult();
    }
}
