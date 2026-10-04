package org.acme.contracts.init;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.contracts.model.*;
import org.acme.contracts.repo.ContractRepository;
import org.acme.contracts.repo.EmployeeRepository;
import org.jboss.logging.Logger;

/** 首次启动写入演示数据：两名管理员（避免自审）、档案员、多名员工和不同密级合同。 */
@ApplicationScoped
public class DataSeeder {

    private static final Logger LOG = Logger.getLogger(DataSeeder.class);

    @Inject
    EmployeeRepository employees;

    @Inject
    ContractRepository contracts;

    @Transactional
    void seed(@Observes StartupEvent ev) {
        if (employees.count() > 0) {
            return;
        }

        Employee admin1 = emp("E001", "周敏", "法务部-合同管理岗", Enums.Role.ADMIN, Enums.Secrecy.SECRET, true);
        Employee admin2 = emp("E002", "李建国", "法务部-总监", Enums.Role.ADMIN, Enums.Secrecy.SECRET, true);
        Employee archivist = emp("E003", "王芳", "法务部-档案员", Enums.Role.ARCHIVIST, Enums.Secrecy.CONFIDENTIAL, true);
        Employee buyer = emp("E101", "张伟", "采购部-专员", Enums.Role.EMPLOYEE, Enums.Secrecy.INTERNAL, false);
        Employee sales = emp("E102", "陈静", "销售部-经理", Enums.Role.EMPLOYEE, Enums.Secrecy.CONFIDENTIAL, true);
        Employee hr = emp("E103", "刘洋", "人事部-主管", Enums.Role.EMPLOYEE, Enums.Secrecy.PUBLIC, false);

        contract("HT-2026-0001", "XX供应商年度采购框架合同", Enums.Secrecy.INTERNAL, "A-01-02", 12, "1,12");
        contract("HT-2026-0002", "YY客户战略合作协议", Enums.Secrecy.CONFIDENTIAL, "B-03-05", 20, "1,20");
        contract("HT-2026-0003", "办公楼租赁合同", Enums.Secrecy.PUBLIC, "C-02-01", 8, "8");
        contract("HT-2026-0004", "技术许可保密合同", Enums.Secrecy.SECRET, "D-01-01", 30, "1,2,30");

        LOG.infof("演示数据已播种: %d 名员工, %d 份合同", employees.count(), contracts.count());
    }

    private Employee emp(String no, String name, String post, Enums.Role role,
                         Enums.Secrecy clearance, boolean scanAuth) {
        Employee e = new Employee();
        e.employeeNo = no;
        e.name = name;
        e.post = post;
        e.role = role;
        e.clearance = clearance;
        e.scanAuthorized = scanAuth;
        e.persist();
        return e;
    }

    private void contract(String code, String title, Enums.Secrecy secrecy,
                          String box, int pages, String stamped) {
        Contract c = new Contract();
        c.code = code;
        c.title = title;
        c.secrecy = secrecy;
        c.boxLocation = box;
        c.pages = pages;
        c.stampedPages = stamped;
        c.persist();
    }
}
