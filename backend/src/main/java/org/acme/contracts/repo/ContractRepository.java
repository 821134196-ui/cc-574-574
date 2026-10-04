package org.acme.contracts.repo;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.contracts.model.Contract;

@ApplicationScoped
public class ContractRepository implements PanacheRepository<Contract> {

    public Contract findByCode(String code) {
        return find("code", code).firstResult();
    }
}
