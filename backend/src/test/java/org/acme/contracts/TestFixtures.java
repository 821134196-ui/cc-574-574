package org.acme.contracts;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.contracts.model.Contract;
import org.acme.contracts.model.Employee;
import org.acme.contracts.model.Loan;
import org.acme.contracts.repo.ContractRepository;
import org.acme.contracts.repo.EmployeeRepository;
import org.acme.contracts.repo.LoanRepository;

import java.time.LocalDate;

/** 测试专用：直接在事务里改库，模拟到期、调岗、密级变化等时间/状态前置条件。 */
@ApplicationScoped
@Unremovable
public class TestFixtures {

    @Inject
    LoanRepository loans;

    @Inject
    ContractRepository contracts;

    @Inject
    EmployeeRepository employees;

    @Transactional
    public void setDueDate(Long loanId, LocalDate due) {
        Loan l = loans.findById(loanId);
        l.dueDate = due;
    }

    @Transactional
    public void setContractSecrecy(Long contractId, org.acme.contracts.model.Enums.Secrecy s) {
        Contract c = contracts.findById(contractId);
        c.secrecy = s;
    }

    @Transactional
    public void setEmployeeClearance(Long employeeId, org.acme.contracts.model.Enums.Secrecy s) {
        Employee e = employees.findById(employeeId);
        e.clearance = s;
        e.postVersion++;
    }

    @Transactional
    public org.acme.contracts.model.Enums.Secrecy getContractSecrecy(Long contractId) {
        return contracts.findById(contractId).secrecy;
    }
}
