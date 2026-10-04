package org.acme.contracts.repo;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.contracts.model.Employee;

@ApplicationScoped
public class EmployeeRepository implements PanacheRepository<Employee> {

    public Employee findByNo(String employeeNo) {
        return find("employeeNo", employeeNo).firstResult();
    }
}
