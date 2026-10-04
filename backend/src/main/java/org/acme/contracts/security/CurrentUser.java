package org.acme.contracts.security;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedMap;
import org.acme.contracts.model.Employee;
import org.acme.contracts.repo.EmployeeRepository;
import org.acme.contracts.service.ApiException;

/**
 * 请求级当前登录人。演示系统不做真实登录，前端通过请求头 X-User-Id 传员工编号。
 */
@RequestScoped
public class CurrentUser {

    @Inject
    EmployeeRepository employees;

    private Employee user;
    private boolean resolved;

    /** 由过滤器在请求开始时解析。 */
    public void bind(Employee employee) {
        this.user = employee;
        this.resolved = true;
    }

    public Employee requireAny() {
        if (!resolved || user == null) {
            throw new ApiException(401, "缺少请求头 X-User-Id（请先选择登录员工）");
        }
        if (!user.active) {
            throw new ApiException(403, "账号已停用：" + user.name);
        }
        return user;
    }

    public Employee requireRole(org.acme.contracts.model.Enums.Role role) {
        Employee e = requireAny();
        if (e.role != role) {
            throw ApiException.forbidden("需要角色 " + role + "，当前用户角色为 " + e.role);
        }
        return e;
    }

    public void resolve(MultivaluedMap<String, String> headers) {
        if (resolved) {
            return;
        }
        String id = headers.getFirst("X-User-Id");
        if (id != null && !id.isBlank()) {
            try {
                bind(employees.findById(Long.parseLong(id.trim())));
            } catch (NumberFormatException ignored) {
                // 非法 id 保持未登录
            }
        }
    }
}
