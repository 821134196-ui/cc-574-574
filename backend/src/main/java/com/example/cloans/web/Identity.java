package com.example.cloans.web;

import com.example.cloans.domain.User;
import com.example.cloans.service.ApiException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * 开发态身份模拟：前端通过 X-User-Id 头切换当前登录用户（无真实认证）。
 */
@RequestScoped
public class Identity {

    @Inject
    IdentityHolder holder;

    @Inject
    EntityManager em;

    private User cached;

    public User current() {
        if (cached != null) return cached;
        String raw = holder == null ? null : holder.userIdHeader;
        long id;
        try {
            id = Long.parseLong(raw == null || raw.isBlank() ? "1" : raw.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("X-User-Id 必须是数字");
        }
        cached = em.find(User.class, id);
        if (cached == null) throw ApiException.notFound("用户不存在: " + id);
        if (!cached.active) throw ApiException.forbidden("账号已停用");
        return cached;
    }
}
