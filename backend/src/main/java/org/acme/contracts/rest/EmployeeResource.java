package org.acme.contracts.rest;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.acme.contracts.model.Employee;
import org.acme.contracts.model.Enums;
import org.acme.contracts.repo.EmployeeRepository;
import org.acme.contracts.security.CurrentUser;
import org.acme.contracts.service.ApiException;
import org.acme.contracts.service.AuditService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Path("/api/employees")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EmployeeResource {

    @Inject
    EmployeeRepository repo;

    @Inject
    CurrentUser currentUser;

    @Inject
    AuditService audit;

    public record EmployeeInput(String employeeNo, String name, String post, Enums.Role role,
                                Enums.Secrecy clearance, Boolean scanAuthorized) {
    }

    @GET
    public List<Employee> list() {
        return repo.listAll();
    }

    @GET
    @Path("/{id}")
    public Employee get(@PathParam("id") Long id) {
        Employee e = repo.findById(id);
        if (e == null) {
            throw ApiException.notFound("员工不存在: " + id);
        }
        return e;
    }

    @POST
    @Transactional
    public Employee create(EmployeeInput in) {
        currentUser.requireRole(Enums.Role.ADMIN);
        if (in.employeeNo() == null || in.employeeNo().isBlank() || in.name() == null || in.name().isBlank()) {
            throw ApiException.badRequest("员工编号和姓名必填");
        }
        if (repo.findByNo(in.employeeNo()) != null) {
            throw ApiException.conflict("员工编号已存在: " + in.employeeNo());
        }
        Employee e = new Employee();
        e.employeeNo = in.employeeNo().trim();
        e.name = in.name().trim();
        e.post = in.post() == null ? "" : in.post().trim();
        e.role = in.role() == null ? Enums.Role.EMPLOYEE : in.role();
        e.clearance = in.clearance() == null ? Enums.Secrecy.INTERNAL : in.clearance();
        e.scanAuthorized = Boolean.TRUE.equals(in.scanAuthorized());
        e.persist();
        audit.append("EMP_CREATE", currentUser.requireAny(), null, null, e.id,
                "{\"no\":\"" + e.employeeNo + "\",\"name\":\"" + e.name + "\"}");
        return e;
    }

    /** 调岗：变更岗位与密级，postVersion +1 触发在途借阅单重新核验。 */
    @PUT
    @Path("/{id}/post")
    @Transactional
    public Employee transfer(@PathParam("id") Long id, EmployeeInput in) {
        Employee admin = currentUser.requireRole(Enums.Role.ADMIN);
        Employee e = repo.findById(id);
        if (e == null) {
            throw ApiException.notFound("员工不存在: " + id);
        }
        String oldPost = e.post;
        Enums.Secrecy oldClearance = e.clearance;
        boolean changed = false;
        if (in.post() != null && !in.post().isBlank()) {
            e.post = in.post().trim();
            changed = true;
        }
        if (in.clearance() != null) {
            e.clearance = in.clearance();
        }
        if (in.role() != null) {
            e.role = in.role();
        }
        if (in.scanAuthorized() != null) {
            e.scanAuthorized = in.scanAuthorized();
        }
        if (changed || in.clearance() != null) {
            e.postVersion++;
        }
        e.updatedAt = Instant.now();
        audit.append("EMP_TRANSFER", admin, null, null, e.id,
                "{\"oldPost\":\"" + oldPost + "\",\"newPost\":\"" + e.post
                        + "\",\"oldClearance\":\"" + oldClearance + "\",\"newClearance\":\""
                        + e.clearance + "\",\"postVersion\":" + e.postVersion + "}");
        return e;
    }

    @PUT
    @Path("/{id}/active")
    @Transactional
    public Map<String, Object> setActive(@PathParam("id") Long id, Map<String, Boolean> body) {
        Employee admin = currentUser.requireRole(Enums.Role.ADMIN);
        Employee e = repo.findById(id);
        if (e == null) {
            throw ApiException.notFound("员工不存在: " + id);
        }
        e.active = Boolean.TRUE.equals(body.get("active"));
        e.updatedAt = Instant.now();
        audit.append(e.active ? "EMP_ENABLE" : "EMP_DISABLE", admin, null, null, e.id, "{}");
        return Map.of("id", e.id, "active", e.active);
    }
}
